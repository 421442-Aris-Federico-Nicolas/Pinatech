package com.computerstore.shipping.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

import com.computerstore.email.*;
import com.computerstore.common.exception.InvalidStateTransitionException;
import com.computerstore.order.domain.*;
import com.computerstore.order.repository.CustomerOrderRepository;
import com.computerstore.order.service.OrderStockService;
import com.computerstore.shipping.config.ZipnovaProperties;
import com.computerstore.shipping.domain.*;
import com.computerstore.shipping.gateway.*;
import com.computerstore.shipping.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShipmentDispatchService {
    private final OrderShipmentRepository shipments; private final ShipmentEventRepository events;
    private final CustomerOrderRepository orders; private final OrderEmailOutboxService outbox;
    private final OrderStockService stock;
    private final ZipnovaProperties properties; private final Clock clock;
    public ShipmentDispatchService(OrderShipmentRepository shipments, ShipmentEventRepository events,
            CustomerOrderRepository orders, OrderEmailOutboxService outbox, OrderStockService stock,
            ZipnovaProperties properties, Clock clock) {
        this.shipments = shipments; this.events = events; this.orders = orders; this.outbox = outbox;
        this.stock = stock; this.properties = properties; this.clock = clock;
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
            CustomerOrder order = orders.findByIdForUpdate(shipment.getOrder().getId()).orElseThrow();
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

    @Transactional
    public void creationSucceeded(UUID id, UUID token, ZipnovaGateway.ProviderShipment provider) {
        OrderShipment shipment = shipments.findByIdForUpdate(id).orElseThrow();
        if (!shipment.getExternalId().equals(provider.externalId())) throw new IllegalArgumentException("Zipnova external ID mismatch.");
        shipment.created(provider, token, Instant.now(clock));
        applyOrderState(shipment, provider, "documentation_ready".equalsIgnoreCase(provider.status()));
    }

    @Transactional
    public void creationFailed(UUID id, UUID token, ShippingProviderException error) {
        shipments.findByIdForUpdate(id).ifPresent(shipment -> {
            if (error.retryable()) shipment.retry(token, Instant.now(clock), error.retryAfter(), error.getMessage());
            else shipment.failedPermanently(token, Instant.now(clock), error.getMessage());
        });
    }

    @Transactional
    public void reconciled(UUID id, UUID token, ZipnovaGateway.ProviderShipment provider,
                           List<ZipnovaGateway.TrackingEvent> history) {
        OrderShipment shipment = shipments.findByIdForUpdate(id).orElseThrow();
        if (!Objects.equals(shipment.getProviderShipmentId(), provider.id())) return;
        if (shipment.update(provider, Instant.now(clock))) {
            persistProviderCancellation(shipment, provider);
            for (var event : history) persistEvent(shipment, event.status(), event.substatus(), event.occurredAt());
            applyOrderState(shipment, provider, documentationReady(provider, history));
        }
        shipment.reconciliationDue(token, Instant.now(clock).plus(properties.reconciliationInterval()));
    }

    @Transactional
    public void reconciliationFailed(UUID id, UUID token) {
        shipments.findByIdForUpdate(id).ifPresent(shipment -> shipment.reconciliationDue(token,
                Instant.now(clock).plus(Duration.ofMinutes(2))));
    }

    @Transactional
    public void applyWebhook(long providerId, ZipnovaGateway.ProviderShipment provider,
                             List<ZipnovaGateway.TrackingEvent> history) {
        OrderShipment shipment = shipments.findByProviderShipmentId(providerId).orElse(null);
        if (shipment == null) return;
        shipment = shipments.findByIdForUpdate(shipment.getId()).orElseThrow();
        if (!Objects.equals(shipment.getProviderShipmentId(), providerId) || provider.id() != providerId) return;
        if (!shipment.update(provider, Instant.now(clock))) return;
        persistProviderCancellation(shipment, provider);
        for (var event : history) persistEvent(shipment, event.status(), event.substatus(), event.occurredAt());
        applyOrderState(shipment, provider, documentationReady(provider, history));
    }

    @Transactional public void retry(Long orderId) {
        OrderShipment candidate = shipments.findByOrderId(orderId).orElseThrow(() ->
                new com.computerstore.common.exception.ResourceNotFoundException("Shipment not found."));
        OrderShipment shipment = shipments.findByIdForUpdate(candidate.getId()).orElseThrow();
        CustomerOrder order = orders.findByIdForUpdate(shipment.getOrder().getId()).orElseThrow();
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
    public void cancelled(Long orderId, long providerId) {
        OrderShipment candidate = shipments.findByOrderId(orderId).orElseThrow(() ->
                new com.computerstore.common.exception.ResourceNotFoundException("Shipment not found."));
        OrderShipment shipment = shipments.findByIdForUpdate(candidate.getId()).orElseThrow();
        if (!Objects.equals(shipment.getProviderShipmentId(), providerId)) {
            throw new InvalidStateTransitionException("The shipment changed while cancellation was being processed.");
        }
        Instant now = Instant.now(clock);
        shipment.cancelled(now);
        persistEvent(shipment, "cancelled", null, now);
        orders.findByIdForUpdate(shipment.getOrder().getId()).orElseThrow().markShipmentCancelled();
    }

    @Transactional
    public void paymentNoLongerApproved(CustomerOrder order) {
        if (order.getFulfillmentMethod() != FulfillmentMethod.DELIVERY) return;
        shipments.findByOrderId(order.getId()).flatMap(shipment -> shipments.findByIdForUpdate(shipment.getId()))
                .ifPresent(shipment -> shipment.paymentNotApproved(Instant.now(clock)));
    }

    private void applyOrderState(OrderShipment shipment, ZipnovaGateway.ProviderShipment provider,
                                 boolean documentationReady) {
        CustomerOrder order = orders.findByIdForUpdate(shipment.getOrder().getId()).orElseThrow();
        if (order.getPaymentStatus() != PaymentStatus.APPROVED) {
            shipment.paymentNotApproved(Instant.now(clock));
            return;
        }
        String status = provider.status().toLowerCase(Locale.ROOT);
        if (Set.of("cancelled", "canceled").contains(status)) {
            order.markShipmentCancelled();
            return;
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
                    provider.carrierTrackingId(), provider.estimatedDelivery(), provider.trackingUrl()));
        }
        if (!damaged && "delivered".equals(status) && order.markAuthoritativelyDelivered()) {
            outbox.enqueueOnce(order, OrderEmailEventType.ORDER_DELIVERED);
        }
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
}
