package com.computerstore.payment.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import com.computerstore.common.exception.InvalidRequestException;
import com.computerstore.common.exception.EmailVerificationRequiredException;
import com.computerstore.common.exception.ReservationExpiredException;
import com.computerstore.common.exception.ResourceNotFoundException;
import com.computerstore.common.exception.BusinessRuleException;
import com.computerstore.order.domain.OrderStatus;
import com.computerstore.order.domain.PaymentMethod;
import com.computerstore.order.repository.CustomerOrderRepository;
import com.computerstore.order.service.OrderStockService;
import com.computerstore.order.service.FulfillmentPolicy;
import com.computerstore.payment.config.MercadoPagoEnvironment;
import com.computerstore.payment.config.MercadoPagoProperties;
import com.computerstore.payment.domain.PaymentAttempt;
import com.computerstore.payment.domain.PaymentAttemptStatus;
import com.computerstore.payment.domain.PaymentEvent;
import com.computerstore.payment.domain.ProviderPaymentRecord;
import com.computerstore.payment.dto.PaymentCheckoutResponse;
import com.computerstore.payment.gateway.PaymentPreference;
import com.computerstore.payment.gateway.PaymentPreferenceRequest;
import com.computerstore.payment.gateway.ProviderPayment;
import com.computerstore.payment.gateway.RefundResult;
import com.computerstore.payment.repository.PaymentAttemptRepository;
import com.computerstore.payment.repository.PaymentEventRepository;
import com.computerstore.payment.repository.ProviderPaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import com.computerstore.email.OrderEmailEventType;
import com.computerstore.email.OrderEmailOutboxService;
import java.util.ArrayList;
import com.computerstore.shipping.service.ShipmentDispatchService;
import org.springframework.beans.factory.ObjectProvider;

@Service
public class PaymentAttemptTransactionalService {

    private static final List<PaymentAttemptStatus> ACTIVE = List.of(
            PaymentAttemptStatus.CREATED, PaymentAttemptStatus.PREFERENCE_CREATED, PaymentAttemptStatus.PENDING);

    private final PaymentAttemptRepository attempts;
    private final ProviderPaymentRepository providerPayments;
    private final PaymentEventRepository events;
    private final CustomerOrderRepository orders;
    private final OrderStockService stock;
    private final MercadoPagoProperties properties;
    private final FulfillmentPolicy fulfillment;
    private final Clock clock;
    private final OrderEmailOutboxService outbox;
    private final ShipmentDispatchService shipmentDispatch;

    public PaymentAttemptTransactionalService(
            PaymentAttemptRepository attempts,
            ProviderPaymentRepository providerPayments,
            PaymentEventRepository events,
            CustomerOrderRepository orders,
            OrderStockService stock,
            MercadoPagoProperties properties,
            FulfillmentPolicy fulfillment,
            Clock clock,
            OrderEmailOutboxService outbox
    ) {
        this.attempts = attempts;
        this.providerPayments = providerPayments;
        this.events = events;
        this.orders = orders;
        this.stock = stock;
        this.properties = properties;
        this.fulfillment = fulfillment;
        this.clock = clock;
        this.outbox = outbox;
        this.shipmentDispatch = null;
    }

    @Autowired
    public PaymentAttemptTransactionalService(PaymentAttemptRepository attempts,
            ProviderPaymentRepository providerPayments, PaymentEventRepository events,
            CustomerOrderRepository orders, OrderStockService stock, MercadoPagoProperties properties,
            FulfillmentPolicy fulfillment, Clock clock, OrderEmailOutboxService outbox,
            ObjectProvider<ShipmentDispatchService> shipmentDispatch) {
        this.attempts = attempts; this.providerPayments = providerPayments; this.events = events; this.orders = orders;
        this.stock = stock; this.properties = properties; this.fulfillment = fulfillment; this.clock = clock;
        this.outbox = outbox; this.shipmentDispatch = shipmentDispatch.getIfAvailable();
    }

    public PaymentAttemptTransactionalService(PaymentAttemptRepository attempts,
            ProviderPaymentRepository providerPayments, PaymentEventRepository events,
            CustomerOrderRepository orders, OrderStockService stock, MercadoPagoProperties properties,
            FulfillmentPolicy fulfillment, Clock clock) {
        this(attempts, providerPayments, events, orders, stock, properties, fulfillment, clock, null);
    }

    @Transactional(noRollbackFor = ReservationExpiredException.class)
    public PaymentPreparation prepare(Long orderId, Long userId, String suppliedIdempotencyKey) {
        properties.requireEnabled();
        String idempotencyKey = normalizeIdempotencyKey(suppliedIdempotencyKey);
        var order = orders.findByIdAndUserIdForUpdate(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found."));
        Instant now = Instant.now(clock);

        if (!order.getUser().isEmailVerified()) {
            throw new EmailVerificationRequiredException();
        }
        if (order.getPaymentMethod() != PaymentMethod.MERCADO_PAGO) {
            throw new InvalidRequestException("Mercado Pago checkout is only available for Mercado Pago orders.");
        }
        fulfillment.validatePayment(order);

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new InvalidRequestException("Only a pending payment order can start a payment.");
        }
        for (PaymentAttempt active : attempts.findActiveByOrderId(orderId, ACTIVE)) {
            if (active.isActiveAt(now)) {
                return active.getCheckoutUrl() == null
                        ? new PaymentPreparation(false, null, preferenceRequest(active))
                        : new PaymentPreparation(false, response(active), null);
            }
            active.retire("Payment preference expired.");
        }
        if (order.isReservationExpired(now)) {
            stock.release(order);
            order.expire();
            throw new ReservationExpiredException("The order reservation has expired.");
        }
        if (!"ARS".equals(order.getCurrency())) {
            throw new InvalidRequestException("Mercado Pago only supports ARS orders.");
        }
        PaymentAttempt attempt = attempts.save(new PaymentAttempt(order, idempotencyKey));
        return new PaymentPreparation(true, null, preferenceRequest(attempt));
    }

    @Transactional
    public PaymentCheckoutResponse completePreference(UUID attemptId, PaymentPreference preference) {
        PaymentAttempt attempt = attemptForUpdate(attemptId);
        if (!attempt.isActiveAt(Instant.now(clock))) {
            throw new InvalidRequestException("The payment preference is no longer active.");
        }
        attempt.preferenceCreated(preference.preferenceId(), preference.checkoutUrl());
        return response(attempt);
    }

    @Transactional
    public void recordPreferenceFailure(UUID attemptId, String error) {
        attemptForUpdate(attemptId).preferenceFailed(error);
    }

    @Transactional
    public Optional<RefundInstruction> processWebhook(
            ProviderPayment payment, String requestedPaymentId, String requestId, String notificationPayload) {
        if (!payment.id().equals(requestedPaymentId)) {
            throw new InvalidRequestException("Mercado Pago returned a different payment ID.");
        }
        return processPayment(payment, requestId, notificationPayload);
    }

    @Transactional
    public Optional<RefundInstruction> processReconciledPayment(ProviderPayment payment) {
        return processPayment(payment, "reconciliation-" + payment.id(), "reconciliation");
    }

    private Optional<RefundInstruction> processPayment(
            ProviderPayment payment, String requestId, String notificationPayload) {
        UUID publicId = parsePublicId(payment.externalReference());
        PaymentAttempt attempt = attemptForUpdate(publicId);
        var order = orders.findByIdForUpdate(attempt.getOrder().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found."));
        validateAuthoritativePayment(attempt, payment);

        Optional<ProviderPaymentRecord> existingPayment = providerPayments
                .findByProviderPaymentIdForUpdate(payment.id());
        ProviderPaymentRecord providerPayment = existingPayment
                .orElseGet(() -> providerPayments.save(new ProviderPaymentRecord(attempt, payment)));
        if (!providerPayment.getAttempt().getPublicId().equals(attempt.getPublicId())) {
            throw new InvalidRequestException("Mercado Pago payment is already linked to another preference.");
        }

        String eventKey = eventKey(payment);
        if (events.findByEventKey(eventKey).isPresent()) {
            return pendingRefund(providerPayment, null);
        }
        PaymentEvent event = events.save(new PaymentEvent(
                attempt, payment.id(), requestId, eventKey, payment.status(), payment.statusDetail(),
                sha256(notificationPayload), payment.payloadHash()));
        if (existingPayment.isPresent() && providerPayment.isStale(payment)) {
            event.processed("STALE_EVENT");
            return pendingRefund(providerPayment, null);
        }
        providerPayment.update(payment);

        String status = payment.status().toLowerCase(Locale.ROOT);
        switch (status) {
            case "pending", "in_process", "authorized" -> {
                attempt.summaryStatus(status, PaymentAttemptStatus.PENDING);
                event.processed("PENDING");
            }
            case "in_mediation" -> {
                providerPayment.mediation();
                if (providerPayment.isFundsOrder()) {
                    order.markPaymentInMediation();
                    shippingPaymentRevoked(order);
                }
                event.processed("MEDIATION");
            }
            case "rejected", "cancelled" -> {
                if (!providerPayment.isFundsOrder()) {
                    // A preference can produce another payment after one rejection.
                    attempt.summaryStatus(status, PaymentAttemptStatus.PENDING);
                }
                event.processed("REJECTED");
            }
            case "approved" -> {
                return processApproval(attempt, providerPayment, event, payment, order);
            }
            case "refunded" -> {
                providerPayment.externallyRefunded();
                if (providerPayment.isFundsOrder()
                        || !providerPayments.existsByAttemptOrderIdAndFundsOrderTrue(order.getId())) {
                    order.markPaymentRefunded();
                    shippingPaymentRevoked(order);
                    attempt.summaryStatus(status, PaymentAttemptStatus.REFUNDED);
                }
                event.processed("REFUNDED_BY_PROVIDER");
            }
            case "charged_back" -> {
                providerPayment.chargeback();
                if (providerPayment.isFundsOrder()) {
                    order.markPaymentChargedBack();
                    shippingPaymentRevoked(order);
                }
                event.processed("CHARGEBACK");
            }
            default -> event.processed("IGNORED_STATUS");
        }
        return Optional.empty();
    }

    private Optional<RefundInstruction> processApproval(
            PaymentAttempt attempt,
            ProviderPaymentRecord providerPayment,
            PaymentEvent event,
            ProviderPayment payment,
            com.computerstore.order.domain.CustomerOrder order
    ) {
        if (providerPayment.isFundsOrder()) {
            order.confirmPaymentApproved();
            if (shipmentDispatch != null) shipmentDispatch.enqueue(order);
            event.processed("ALREADY_FUNDED");
            return Optional.empty();
        }
        if (providerPayment.getRefundIdempotencyKey() != null) {
            event.processed("REFUND_PENDING");
            return pendingRefund(providerPayment, event.getId());
        }
        boolean alreadyFunded = providerPayments.existsByAttemptOrderIdAndFundsOrderTrue(order.getId());
        boolean approvedBeforeExpiry = payment.approvedAt() != null
                && !payment.approvedAt().isAfter(attempt.getExpiresAt());
        boolean fulfillmentEligible = true;
        try {
            fulfillment.validatePayment(order);
        } catch (BusinessRuleException exception) {
            fulfillmentEligible = false;
        }
        if (!alreadyFunded && order.getStatus() == OrderStatus.PENDING_PAYMENT
                && approvedBeforeExpiry && fulfillmentEligible) {
            providerPayment.fundsOrder();
            order.approveMercadoPagoPayment();
            if (outbox != null) outbox.enqueue(order, OrderEmailEventType.PAYMENT_APPROVED);
            if (shipmentDispatch != null) shipmentDispatch.enqueue(order);
            attempt.summaryStatus(payment.status(), PaymentAttemptStatus.APPROVED);
            event.processed("ORDER_PAID");
            return Optional.empty();
        }
        if (!alreadyFunded && order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            stock.release(order);
            order.expire();
        }
        UUID refundKey = providerPayment.requestRefund(Instant.now(clock));
        if (!alreadyFunded) {
            order.markPaymentRefundPending();
            attempt.summaryStatus(payment.status(), PaymentAttemptStatus.REFUND_PENDING);
        }
        event.processed("REFUND_PENDING");
        return Optional.of(new RefundInstruction(
                attempt.getPublicId(), payment.id(), refundKey, providerPayment.getRefundId(), event.getId()));
    }

    @Transactional
    public void applyRefundResult(RefundInstruction instruction, RefundResult result) {
        ProviderPaymentRecord payment = providerPayments
                .findByProviderPaymentIdForUpdate(instruction.paymentId())
                .orElseThrow(() -> new ResourceNotFoundException("Provider payment not found."));
        if (!instruction.idempotencyKey().equals(payment.getRefundIdempotencyKey())) {
            throw new IllegalStateException("Refund result does not match the pending refund.");
        }
        var order = orders.findByIdForUpdate(payment.getAttempt().getOrder().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found."));
        payment.refundResult(result, Instant.now(clock));
        if (payment.refundTerminalAndComplete()) {
            if (!providerPayments.existsByAttemptOrderIdAndFundsOrderTrue(order.getId())) {
                order.markPaymentRefunded();
                shippingPaymentRevoked(order);
                payment.getAttempt().summaryStatus("refunded", PaymentAttemptStatus.REFUNDED);
            }
            event(instruction.eventId()).ifPresent(value -> value.processed("REFUNDED"));
        } else {
            event(instruction.eventId()).ifPresent(value -> value.processed(
                    "REJECTED".equals(payment.getRefundStatus()) ? "REFUND_REJECTED" : "REFUND_PENDING"));
        }
    }

    @Transactional
    public void refundFailed(RefundInstruction instruction, String error) {
        providerPayments.findByProviderPaymentIdForUpdate(instruction.paymentId())
                .filter(payment -> instruction.idempotencyKey().equals(payment.getRefundIdempotencyKey()))
                .ifPresent(payment -> payment.refundFailed(error, Instant.now(clock)));
        event(instruction.eventId()).ifPresent(value -> value.processed("REFUND_FAILED"));
    }

    @Transactional
    public List<RefundInstruction> claimPendingRefunds() {
        Instant now = Instant.now(clock);
        return providerPayments.lockRefundsDue(now).stream().map(payment -> {
            payment.lease(now.plusSeconds(60));
            return new RefundInstruction(
                    payment.getAttempt().getPublicId(), payment.getProviderPaymentId(),
                    payment.getRefundIdempotencyKey(), payment.getRefundId(), null);
        }).toList();
    }

    @Transactional
    public List<ReconciliationInstruction> claimReconciliations() {
        Instant now = Instant.now(clock);
        Instant cutoff = now.minus(properties.reconciliationLookback());
        return attempts.lockReconciliationsDue(now, cutoff).stream().map(attempt -> {
            attempt.reconciliationLease(now.plusSeconds(60));
            return new ReconciliationInstruction(attempt.getPublicId(), attempt.getPreferenceId());
        }).toList();
    }

    @Transactional
    public void reconciliationSucceeded(UUID attemptId) {
        attemptForUpdate(attemptId).reconciliationSucceeded(Instant.now(clock));
    }

    @Transactional
    public void reconciliationFailed(UUID attemptId, String error) {
        attemptForUpdate(attemptId).reconciliationFailed(Instant.now(clock), error);
    }

    private Optional<RefundInstruction> pendingRefund(ProviderPaymentRecord payment, Long eventId) {
        if (payment.getRefundIdempotencyKey() == null || payment.refundTerminalAndComplete()) {
            return Optional.empty();
        }
        return Optional.of(new RefundInstruction(
                payment.getAttempt().getPublicId(), payment.getProviderPaymentId(),
                payment.getRefundIdempotencyKey(), payment.getRefundId(), eventId));
    }

    private void shippingPaymentRevoked(com.computerstore.order.domain.CustomerOrder order) {
        if (shipmentDispatch != null) shipmentDispatch.paymentNoLongerApproved(order);
    }

    private void validateAuthoritativePayment(PaymentAttempt attempt, ProviderPayment payment) {
        boolean expectedLiveMode = properties.environment() == MercadoPagoEnvironment.PRODUCTION;
        if (!attempt.getPublicId().toString().equals(payment.externalReference())
                || attempt.getPreferenceId() == null
                || !attempt.getPreferenceId().equals(payment.preferenceId())
                || !properties.collectorId().equals(payment.collectorId())
                || attempt.getAmount().compareTo(payment.amount()) != 0
                || !"ARS".equals(payment.currency())
                || !attempt.getCurrency().equals(payment.currency())
                || payment.liveMode() != expectedLiveMode
                || !"regular_payment".equals(payment.operationType())
                || payment.amountRefunded() == null
                || payment.amountRefunded().signum() < 0
                || payment.amountRefunded().compareTo(payment.amount()) > 0) {
            throw new InvalidRequestException("Mercado Pago payment data does not match the payment attempt.");
        }
    }

    private PaymentPreferenceRequest preferenceRequest(PaymentAttempt attempt) {
        var items = new ArrayList<PaymentPreferenceRequest.Item>();
        items.addAll(attempt.getOrder().getItems().stream()
                .map(item -> new PaymentPreferenceRequest.Item(
                        item.getVariant().getId().toString(),
                        item.getProductName() + " - " + item.getVariantColorName(),
                        item.getQuantity(), item.getUnitPrice()))
                .toList());
        // Only historical orders can carry this persisted surcharge.
        if (attempt.getOrder().getPaymentSurcharge().signum() > 0) {
            items.add(new PaymentPreferenceRequest.Item(
                    "MERCADO_PAGO_SURCHARGE", "Recargo Mercado Pago", 1,
                    attempt.getOrder().getPaymentSurcharge()));
        }
        if (attempt.getOrder().getShippingCost().signum() > 0) {
            items.add(new PaymentPreferenceRequest.Item(
                    "ENVIO_ZIPNOVA", "Envio Zipnova", 1, attempt.getOrder().getShippingCost()));
        }
        return new PaymentPreferenceRequest(
                attempt.getPublicId(), attempt.getOrder().getId(), attempt.getAmount(), attempt.getCurrency(),
                attempt.getExpiresAt(), items);
    }

    private PaymentCheckoutResponse response(PaymentAttempt attempt) {
        return new PaymentCheckoutResponse(attempt.getPublicId(), attempt.getOrder().getId(),
                attempt.getStatus().name(), attempt.getCheckoutUrl(), attempt.getExpiresAt());
    }

    private PaymentAttempt attemptForUpdate(UUID publicId) {
        return attempts.findByPublicIdForUpdate(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment attempt not found."));
    }

    private Optional<PaymentEvent> event(Long eventId) {
        return eventId == null ? Optional.empty() : events.findById(eventId);
    }

    private UUID parsePublicId(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw new InvalidRequestException("Invalid payment external reference.");
        }
    }

    private String eventKey(ProviderPayment payment) {
        return sha256(String.join("|", payment.id(), payment.status(), nullToEmpty(payment.statusDetail()),
                payment.lastUpdatedAt() == null ? "" : payment.lastUpdatedAt().toString()));
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    private String normalizeIdempotencyKey(String suppliedKey) {
        if (suppliedKey == null || suppliedKey.trim().isEmpty() || suppliedKey.trim().length() > 100) {
            throw new InvalidRequestException("Idempotency-Key must contain between 1 and 100 characters.");
        }
        return suppliedKey.trim();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
