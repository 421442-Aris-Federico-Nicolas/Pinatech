package com.computerstore.shipping.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

import com.computerstore.email.*;
import com.computerstore.common.exception.InvalidStateTransitionException;
import com.computerstore.order.domain.*;
import com.computerstore.order.repository.CustomerOrderRepository;
import com.computerstore.order.dto.OrderResponse;
import com.computerstore.order.dto.OrderResponseMapper;
import com.computerstore.order.service.OrderStockService;
import com.computerstore.payment.domain.ProviderPaymentRecord;
import com.computerstore.payment.domain.PaymentAttemptStatus;
import com.computerstore.payment.repository.PaymentAttemptRepository;
import com.computerstore.payment.repository.ProviderPaymentRepository;
import com.computerstore.payment.service.RefundInstruction;
import com.computerstore.shipping.config.ZipnovaProperties;
import com.computerstore.shipping.domain.*;
import com.computerstore.shipping.gateway.*;
import com.computerstore.shipping.repository.*;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShipmentDispatchService {
    private final OrderShipmentRepository shipments; private final ShipmentEventRepository events;
    private final CustomerOrderRepository orders; private final OrderEmailOutboxService outbox;
    private final OrderStockService stock;
    private final ProviderPaymentRepository providerPayments;
    private final PaymentAttemptRepository paymentAttempts;
    private final EntityManager entityManager;
    private final ZipnovaProperties properties; private final Clock clock;
    public ShipmentDispatchService(OrderShipmentRepository shipments, ShipmentEventRepository events,
            CustomerOrderRepository orders, OrderEmailOutboxService outbox, OrderStockService stock,
            ProviderPaymentRepository providerPayments, PaymentAttemptRepository paymentAttempts,
            EntityManager entityManager,
            ZipnovaProperties properties, Clock clock) {
        this.shipments = shipments; this.events = events; this.orders = orders; this.outbox = outbox;
        this.stock = stock; this.providerPayments = providerPayments; this.paymentAttempts = paymentAttempts;
        this.entityManager = entityManager;
        this.properties = properties; this.clock = clock;
    }

    public void enqueue(CustomerOrder order) {
        if (order.getFulfillmentMethod() != FulfillmentMethod.DELIVERY) return;
        Optional<OrderShipment> existing = shipments.findByOrderId(order.getId());
        if (existing.isEmpty()) {
            shipments.save(new OrderShipment(order, properties.source(), Instant.now(clock)));
        } else if (existing.get().getStatus() == OrderShipmentStatus.BLOCKED_PAYMENT
                && order.getPaymentStatus() == PaymentStatus.APPROVED) {
            existing.get().retryNow(Instant.now(clock));
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<CreateInstruction> claimCreation() {
        Instant now = Instant.now(clock);
        return shipments.findNextCreationForUpdate(now).flatMap(shipments::findByIdForUpdate).flatMap(shipment -> {
            CustomerOrder order = orders.findById(shipment.getOrder().getId()).orElseThrow();
            if (order.getPaymentStatus() != PaymentStatus.APPROVED) {
                shipment.blockForPayment(now);
                return Optional.empty();
            }
            UUID token = shipment.lease(now);
            List<ZipnovaGateway.Item> items = new ArrayList<>();
            for (OrderItem item : order.getItems()) for (int i = 0; i < item.getQuantity(); i++) {
                if (item.getShippingWeightGrams() == null) throw new IllegalStateException("Order shipping snapshot is incomplete.");
                items.add(new ZipnovaGateway.Item(item.getShippingWeightGrams(), item.getShippingHeightCm(),
                        item.getShippingWidthCm(), item.getShippingLengthCm(), item.getShippingClassificationId(),
                        item.getProductName(), Boolean.TRUE.equals(item.getMustKeepVertical())));
            }
            var command = new ZipnovaGateway.CreateShipmentCommand(shipment.getExternalId(),
                    order.getDeliveryAddress().toDestination(), order.getSubtotal(), order.getShippingServiceCode(),
                    order.getShippingLogisticType(), order.getShippingCarrierId(), List.copyOf(items));
            return Optional.of(new CreateInstruction(shipment.getId(), token, shipment.getExternalId(), command));
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<ReconcileInstruction> claimReconciliation() {
        Instant now = Instant.now(clock);
        return shipments.findNextReconciliationForUpdate(now).flatMap(shipments::findByIdForUpdate)
                .map(shipment -> new ReconcileInstruction(shipment.getId(), shipment.leaseReconciliation(now),
                         shipment.getProviderShipmentId()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<CancellationRetryInstruction> claimCancellation() {
        Instant now = Instant.now(clock);
        return shipments.findNextCancellationForUpdate(now).flatMap(shipments::findByIdForUpdate)
                .map(shipment -> new CancellationRetryInstruction(shipment.getId(), shipment.leaseReconciliation(now),
                        shipment.getOrder().getId(), shipment.getProviderShipmentId(),
                        shipment.getStatus() != OrderShipmentStatus.CANCELLED));
    }

    @Transactional
    public void creationSucceeded(UUID id, UUID token, ZipnovaGateway.ProviderShipment provider) {
        OrderShipment candidate = shipments.findById(id).orElseThrow();
        CustomerOrder order = orders.findByIdForUpdate(candidate.getOrder().getId()).orElseThrow();
        OrderShipment shipment = shipments.findByIdForUpdate(id).orElseThrow();
        if (!shipment.getExternalId().equals(provider.externalId())) throw new IllegalArgumentException("Zipnova external ID mismatch.");
        shipment.created(provider, token, Instant.now(clock));
        persistProviderCancellation(shipment, provider);
        applyOrderState(order, shipment, provider, "documentation_ready".equalsIgnoreCase(provider.status()), null);
    }

    @Transactional
    public void creationFailed(UUID id, UUID token, ShippingProviderException error) {
        shipments.findByIdForUpdate(id).ifPresent(shipment -> {
            if (error.retryable()) shipment.retry(token, Instant.now(clock), error.retryAfter(), error.getMessage());
            else shipment.failedPermanently(token, Instant.now(clock), error.getMessage());
        });
    }

    @Transactional
    public Optional<RefundInstruction> reconciled(UUID id, UUID token, ZipnovaGateway.ProviderShipment provider,
                           List<ZipnovaGateway.TrackingEvent> history, boolean trackingComplete) {
        OrderShipment candidate = shipments.findById(id).orElseThrow();
        ProviderPaymentRecord payment = lockCancellationPayment(candidate.getOrder(), candidate.getCancellationScope());
        CustomerOrder order = orders.findByIdForUpdate(candidate.getOrder().getId()).orElseThrow();
        OrderShipment shipment = shipments.findByIdForUpdate(id).orElseThrow();
        entityManager.refresh(shipment);
        if (!Objects.equals(shipment.getProviderShipmentId(), provider.id())) return Optional.empty();
        Optional<RefundInstruction> refund = Optional.empty();
        boolean updated = shipment.update(provider, Instant.now(clock));
        if (updated) {
            persistProviderCancellation(shipment, provider);
            refund = applyOrderState(order, shipment, provider, documentationReady(provider, history), payment);
        }
        for (var event : history) persistEvent(shipment, event.status(), event.substatus(), event.occurredAt());
        shipment.trackingSyncResult(trackingComplete);
        Duration delay = shipment.isCancellationRequested() ? Duration.ofMinutes(2) : properties.reconciliationInterval();
        shipment.reconciliationDue(token, Instant.now(clock).plus(delay));
        return refund;
    }

    @Transactional
    public void reconciliationFailed(UUID id, UUID token) {
        shipments.findByIdForUpdate(id).ifPresent(shipment -> shipment.reconciliationDue(token,
                Instant.now(clock).plus(Duration.ofMinutes(2))));
    }

    @Transactional
    public Optional<RefundInstruction> applyWebhook(long providerId, ZipnovaGateway.ProviderShipment provider,
                              List<ZipnovaGateway.TrackingEvent> history, boolean trackingComplete) {
        OrderShipment candidate = shipments.findByProviderShipmentId(providerId).orElse(null);
        if (candidate == null) return Optional.empty();
        if (!Objects.equals(candidate.getProviderShipmentId(), providerId)) return Optional.empty();
        ProviderPaymentRecord payment = lockCancellationPayment(candidate.getOrder(), candidate.getCancellationScope());
        CustomerOrder order = orders.findByIdForUpdate(candidate.getOrder().getId()).orElseThrow();
        OrderShipment shipment = shipments.findByIdForUpdate(candidate.getId()).orElseThrow();
        entityManager.refresh(shipment);
        if (!Objects.equals(shipment.getProviderShipmentId(), providerId) || provider.id() != providerId) return Optional.empty();
        boolean updated = shipment.update(provider, Instant.now(clock));
        if (updated) persistProviderCancellation(shipment, provider);
        for (var event : history) persistEvent(shipment, event.status(), event.substatus(), event.occurredAt());
        shipment.trackingSyncResult(trackingComplete);
        return updated ? applyOrderState(order, shipment, provider, documentationReady(provider, history), payment)
                : Optional.empty();
    }

    @Transactional public void retry(Long orderId) {
        CustomerOrder order = orders.findByIdForUpdate(orderId).orElseThrow(() ->
                new com.computerstore.common.exception.ResourceNotFoundException("Order not found."));
        OrderShipment candidate = shipments.findByOrderId(orderId).orElseThrow(() ->
                new com.computerstore.common.exception.ResourceNotFoundException("Shipment not found."));
        OrderShipment shipment = shipments.findByIdForUpdate(candidate.getId()).orElseThrow();
        entityManager.refresh(shipment);
        if (order.getFulfillmentMethod() != FulfillmentMethod.DELIVERY
                || order.getPaymentStatus() != PaymentStatus.APPROVED
                || order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.DELIVERED) {
            throw new InvalidStateTransitionException("This order is not eligible for shipment retry.");
        }
        if (shipment.getStatus() == OrderShipmentStatus.CANCELLED) {
            shipment.replaceCancelled(properties.source(), Instant.now(clock));
            order.markShipmentReplacementPending();
        } else if (shipment.getStatus() == OrderShipmentStatus.RETRY
                || shipment.getStatus() == OrderShipmentStatus.FAILED) {
            shipment.retryNow(Instant.now(clock));
        } else {
            throw new InvalidStateTransitionException("Only failed, retrying or cancelled shipments can be retried.");
        }
    }

    @Transactional
    public CancellationInstruction requestCancellation(Long orderId, ShipmentCancellationScope scope,
            OrderCancellationReason reason, String internalDetail, Long adminId) {
        CustomerOrder candidateOrder = orders.findById(orderId).orElseThrow(() ->
                new com.computerstore.common.exception.ResourceNotFoundException("Order not found."));
        OrderShipment candidate = shipments.findByOrderId(orderId).orElseThrow(() ->
                new com.computerstore.common.exception.ResourceNotFoundException("Shipment not found."));
        if (candidate.isCancellationRequested()) validateMatchingCancellation(candidate, scope, reason, internalDetail);
        else validateCancellation(candidateOrder, candidate, scope, reason, internalDetail);
        lockCancellationPayment(candidateOrder, scope);
        CustomerOrder order = orders.findByIdForUpdate(orderId).orElseThrow();
        OrderShipment shipment = shipments.findByIdForUpdate(candidate.getId()).orElseThrow();
        entityManager.refresh(shipment);
        if (shipment.isCancellationRequested()) {
            validateMatchingCancellation(shipment, scope, reason, internalDetail);
            boolean finalizationRequired = scope == ShipmentCancellationScope.ORDER
                    && shipment.getStatus() == OrderShipmentStatus.CANCELLED
                    && order.getStatus() != OrderStatus.CANCELLED;
            return new CancellationInstruction(orderId, shipment.getProviderShipmentId(), false, finalizationRequired);
        }
        validateCancellation(order, shipment, scope, reason, internalDetail);
        boolean alreadyCancelled = shipment.getStatus() == OrderShipmentStatus.CANCELLED;
        shipment.requestCancellation(scope, reason, internalDetail, adminId, Instant.now(clock));
        return new CancellationInstruction(orderId, shipment.getProviderShipmentId(), !alreadyCancelled, true);
    }

    private void validateCancellation(CustomerOrder order, OrderShipment shipment, ShipmentCancellationScope scope,
            OrderCancellationReason reason, String internalDetail) {
        if (order.getFulfillmentMethod() != FulfillmentMethod.DELIVERY
                || order.getPaymentStatus() != PaymentStatus.APPROVED
                || order.getStatus() == OrderStatus.SHIPPED || order.getStatus() == OrderStatus.DELIVERED
                || order.getStatus() == OrderStatus.CANCELLED || shipment.getProviderShipmentId() == null) {
            throw new InvalidStateTransitionException("This order is not eligible for shipment cancellation.");
        }
        boolean alreadyCancelled = shipment.getStatus() == OrderShipmentStatus.CANCELLED;
        String providerStatus = Objects.toString(shipment.getRawStatus(), "").toLowerCase(Locale.ROOT);
        if (!alreadyCancelled && (shipment.getStatus() != OrderShipmentStatus.ACTIVE
                || !Set.of("new", "documentation_ready").contains(providerStatus))) {
            throw new InvalidStateTransitionException("Zipnova only allows cancellation before dispatch.");
        }
        if (alreadyCancelled && scope != ShipmentCancellationScope.ORDER) {
            throw new InvalidStateTransitionException("The shipment is already cancelled.");
        }
        if (scope == ShipmentCancellationScope.ORDER && reason == null) {
            throw new InvalidStateTransitionException("Cancelling the order requires a reason.");
        }
        if (scope == ShipmentCancellationScope.SHIPMENT_ONLY && (reason != null
                || internalDetail != null && !internalDetail.isBlank())) {
            throw new InvalidStateTransitionException("Shipment-only cancellation cannot contain an order reason.");
        }
    }

    private void validateMatchingCancellation(OrderShipment shipment, ShipmentCancellationScope scope,
            OrderCancellationReason reason, String internalDetail) {
        if (!shipment.matchesCancellation(scope, reason, internalDetail)) {
            throw new InvalidStateTransitionException("A different shipment cancellation is already being processed.");
        }
    }

    @Transactional
    public Optional<RefundInstruction> cancellationSucceeded(Long orderId, long providerId) {
        OrderShipment candidate = shipments.findByOrderId(orderId).orElseThrow(() ->
                new com.computerstore.common.exception.ResourceNotFoundException("Shipment not found."));
        return completeCancellation(candidate, null, orderId, providerId);
    }

    @Transactional
    public Optional<RefundInstruction> cancellationSucceeded(UUID id, UUID token, Long orderId, long providerId) {
        OrderShipment candidate = shipments.findById(id).orElseThrow(() ->
                new com.computerstore.common.exception.ResourceNotFoundException("Shipment not found."));
        return completeCancellation(candidate, token, orderId, providerId);
    }

    private Optional<RefundInstruction> completeCancellation(OrderShipment candidate, UUID token,
            Long orderId, long providerId) {
        ProviderPaymentRecord payment = lockCancellationPayment(candidate.getOrder(), candidate.getCancellationScope());
        CustomerOrder order = orders.findByIdForUpdate(orderId).orElseThrow();
        OrderShipment shipment = shipments.findByIdForUpdate(candidate.getId()).orElseThrow();
        entityManager.refresh(shipment);
        if (!Objects.equals(shipment.getOrder().getId(), orderId)
                || !Objects.equals(shipment.getProviderShipmentId(), providerId)) {
            throw new InvalidStateTransitionException("The shipment changed while cancellation was being processed.");
        }
        Instant now = Instant.now(clock);
        boolean recordCancellationEvent = shipment.getStatus() != OrderShipmentStatus.CANCELLED;
        if (token == null) shipment.cancelled(now);
        else shipment.cancellationCompleted(token, now);
        if (recordCancellationEvent) {
            persistEvent(shipment, "cancelled", null, now);
        }
        return finalizeCancellation(order, shipment, payment, now);
    }

    @Transactional
    public void cancellationFailed(UUID id, UUID token, String error) {
        shipments.findByIdForUpdate(id).ifPresent(shipment ->
                shipment.cancellationFailed(token, Instant.now(clock), error));
    }

    @Transactional
    public void cancellationRejected(UUID id, UUID token, String error) {
        shipments.findByIdForUpdate(id).ifPresent(shipment ->
                shipment.cancellationRejected(token, Instant.now(clock), error));
    }

    @Transactional
    public OrderResponse confirmBankTransferRefund(Long orderId, String reference, Long adminId) {
        CustomerOrder order = orders.findByIdForUpdate(orderId).orElseThrow(() ->
                new com.computerstore.common.exception.ResourceNotFoundException("Order not found."));
        if (order.confirmBankTransferRefund(reference, adminId, Instant.now(clock))) {
            outbox.enqueueOnce(order, OrderEmailEventType.PAYMENT_REFUNDED);
        }
        return OrderResponseMapper.toResponse(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse orderResponse(Long orderId) {
        return OrderResponseMapper.toResponse(orders.findById(orderId).orElseThrow(() ->
                new com.computerstore.common.exception.ResourceNotFoundException("Order not found.")));
    }

    @Transactional
    public void paymentNoLongerApproved(CustomerOrder order) {
        if (order.getFulfillmentMethod() != FulfillmentMethod.DELIVERY) return;
        shipments.findByOrderId(order.getId()).flatMap(shipment -> shipments.findByIdForUpdate(shipment.getId()))
                .ifPresent(shipment -> shipment.paymentNotApproved(Instant.now(clock)));
    }

    private Optional<RefundInstruction> applyOrderState(CustomerOrder order, OrderShipment shipment,
                                  ZipnovaGateway.ProviderShipment provider, boolean documentationReady,
                                  ProviderPaymentRecord cancellationPayment) {
        String status = provider.status().toLowerCase(Locale.ROOT);
        if (Set.of("cancelled", "canceled").contains(status)) {
            return finalizeCancellation(order, shipment, cancellationPayment, Instant.now(clock));
        }
        if (shipment.isCancellationRequested()) {
            shipment.cancellationPending(Instant.now(clock), null);
            return Optional.empty();
        }
        if (order.getPaymentStatus() != PaymentStatus.APPROVED) {
            shipment.paymentNotApproved(Instant.now(clock));
            return Optional.empty();
        }
        boolean damaged = shipment.isIncident() || "delivered_with_damage".equals(status)
                || "delivered_with_damage".equalsIgnoreCase(provider.substatus());
        boolean moving = Set.of("shipped", "in_transit", "out_for_delivery", "delivered", "delivered_with_damage").contains(status);
        if (moving && order.getStatus() == OrderStatus.PAID
                && order.getPaymentStatus() == PaymentStatus.APPROVED) {
            stock.consume(order);
            order.transitionTo(OrderStatus.PREPARING);
        }
        if (moving && order.getPaymentStatus() == PaymentStatus.APPROVED) order.markAuthoritativelyShipped();
        if (documentationReady
                && (provider.carrierTrackingId() != null || provider.trackingUrl() != null)) {
            outbox.enqueueTracking(order, new ShipmentTrackingSnapshot(provider.carrierName(),
                    provider.carrierTrackingId(), provider.estimatedDelivery(), provider.trackingUrl()), shipment.getExternalId());
        }
        if (!damaged && "delivered".equals(status) && order.markAuthoritativelyDelivered()) {
            outbox.enqueueOnce(order, OrderEmailEventType.ORDER_DELIVERED);
        }
        return Optional.empty();
    }

    private ProviderPaymentRecord lockCancellationPayment(CustomerOrder order, ShipmentCancellationScope scope) {
        if (scope != ShipmentCancellationScope.ORDER
                || order.getPaymentMethod() != PaymentMethod.MERCADO_PAGO) return null;
        paymentAttempts.findFundingAttemptByOrderIdForUpdate(order.getId())
                .orElseThrow(() -> new IllegalStateException("Approved Mercado Pago payment attempt not found."));
        return providerPayments.findFundsPaymentByOrderIdForUpdate(order.getId())
                .orElseThrow(() -> new IllegalStateException("Approved Mercado Pago payment not found."));
    }

    private Optional<RefundInstruction> finalizeCancellation(CustomerOrder order, OrderShipment shipment,
            ProviderPaymentRecord payment, Instant now) {
        if (shipment.getCancellationScope() != ShipmentCancellationScope.ORDER) {
            order.markShipmentCancelled();
            return Optional.empty();
        }
        OrderStatus previous = order.getStatus();
        if (!order.cancelForRefund(shipment.getCancellationReason(), shipment.getCancellationInternalDetail(),
                shipment.getCancellationRequestedByUserId(), now)) return Optional.empty();
        if (previous == OrderStatus.PAID) stock.release(order);
        else if (previous == OrderStatus.PREPARING || previous == OrderStatus.READY) stock.restore(order);
        outbox.enqueue(order, OrderEmailEventType.ORDER_CANCELLED, shipment.getCancellationReason().customerLabel());
        if (order.getPaymentMethod() != PaymentMethod.MERCADO_PAGO) return Optional.empty();
        if (payment == null) throw new IllegalStateException("Approved Mercado Pago payment not found.");
        UUID refundKey = payment.requestRefund(now);
        payment.getAttempt().summaryStatus(payment.getProviderStatus(), PaymentAttemptStatus.REFUND_PENDING);
        return Optional.of(new RefundInstruction(payment.getAttempt().getPublicId(), payment.getProviderPaymentId(),
                refundKey, payment.getRefundId(), null));
    }

    private boolean documentationReady(ZipnovaGateway.ProviderShipment provider,
            List<ZipnovaGateway.TrackingEvent> history) {
        return "documentation_ready".equalsIgnoreCase(provider.status()) || history.stream()
                .anyMatch(event -> "documentation_ready".equalsIgnoreCase(event.status()));
    }

    private void persistEvent(OrderShipment shipment, String status, String substatus, Instant occurred) {
        String key = hash(shipment.getProviderShipmentId() + "|" + status + "|" + Objects.toString(substatus, "") + "|" + occurred);
        if (!events.existsByEventKey(key)) events.save(new ShipmentEvent(shipment, key, status, substatus, occurred, Instant.now(clock)));
    }
    private void persistProviderCancellation(OrderShipment shipment, ZipnovaGateway.ProviderShipment provider) {
        String status = provider.status().toLowerCase(Locale.ROOT);
        if (Set.of("cancelled", "canceled").contains(status)) {
            persistEvent(shipment, provider.status(), provider.substatus(),
                    provider.updatedAt() == null ? Instant.now(clock) : provider.updatedAt());
        }
    }
    private String hash(String value) { try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception error) { throw new IllegalStateException(error); } }
    public record CreateInstruction(UUID id, UUID token, String externalId, ZipnovaGateway.CreateShipmentCommand command) {}
    public record ReconcileInstruction(UUID id, UUID token, long providerId) {}
    public record CancellationRetryInstruction(UUID id, UUID token, Long orderId, long providerId,
                                               boolean providerCancellationRequired) {}
    public record CancellationInstruction(Long orderId, long providerId, boolean providerCancellationRequired,
                                          boolean finalizationRequired) {}
}
