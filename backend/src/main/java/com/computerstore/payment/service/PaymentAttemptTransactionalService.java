package com.computerstore.payment.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.computerstore.common.exception.InvalidRequestException;
import com.computerstore.common.exception.ReservationExpiredException;
import com.computerstore.common.exception.ResourceNotFoundException;
import com.computerstore.order.domain.OrderStatus;
import com.computerstore.order.repository.CustomerOrderRepository;
import com.computerstore.order.service.OrderStockService;
import com.computerstore.payment.config.MercadoPagoProperties;
import com.computerstore.payment.domain.PaymentAttempt;
import com.computerstore.payment.domain.PaymentAttemptStatus;
import com.computerstore.payment.domain.PaymentEvent;
import com.computerstore.payment.dto.PaymentCheckoutResponse;
import com.computerstore.payment.gateway.PaymentPreference;
import com.computerstore.payment.gateway.PaymentPreferenceRequest;
import com.computerstore.payment.gateway.ProviderPayment;
import com.computerstore.payment.repository.PaymentAttemptRepository;
import com.computerstore.payment.repository.PaymentEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentAttemptTransactionalService {

    private final PaymentAttemptRepository attempts;
    private final PaymentEventRepository events;
    private final CustomerOrderRepository orders;
    private final OrderStockService stock;
    private final MercadoPagoProperties properties;

    public PaymentAttemptTransactionalService(
            PaymentAttemptRepository attempts,
            PaymentEventRepository events,
            CustomerOrderRepository orders,
            OrderStockService stock,
            MercadoPagoProperties properties
    ) {
        this.attempts = attempts;
        this.events = events;
        this.orders = orders;
        this.stock = stock;
        this.properties = properties;
    }

    @Transactional(noRollbackFor = ReservationExpiredException.class)
    public PaymentPreparation prepare(Long orderId, Long userId, String suppliedIdempotencyKey) {
        properties.requireEnabled();
        String idempotencyKey = normalizeIdempotencyKey(suppliedIdempotencyKey);
        var order = orders.findByIdAndUserIdForUpdate(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found."));
        Optional<PaymentAttempt> existing = attempts.findByOrderIdAndIdempotencyKey(orderId, idempotencyKey);
        if (existing.isPresent()) {
            PaymentAttempt attempt = existing.get();
            if (attempt.getCheckoutUrl() != null) {
                return new PaymentPreparation(false, response(attempt), null);
            }
            return new PaymentPreparation(false, null, preferenceRequest(attempt));
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new InvalidRequestException("Only a pending payment order can start a payment.");
        }
        if (order.isReservationExpired(Instant.now())) {
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
        attempt.preferenceCreated(preference.preferenceId(), preference.checkoutUrl());
        return response(attempt);
    }

    @Transactional
    public void recordPreferenceFailure(UUID attemptId, String error) {
        attemptForUpdate(attemptId).preferenceFailed(error);
    }

    @Transactional
    public Optional<RefundInstruction> processWebhook(
            ProviderPayment payment,
            String requestedPaymentId,
            String requestId,
            String notificationPayload
    ) {
        if (!payment.id().equals(requestedPaymentId)) {
            throw new InvalidRequestException("Mercado Pago returned a different payment ID.");
        }
        UUID publicId = parsePublicId(payment.externalReference());
        PaymentAttempt attempt = attemptForUpdate(publicId);
        var order = orders.findByIdForUpdate(attempt.getOrder().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found."));
        validateAuthoritativePayment(attempt, payment);

        String eventKey = eventKey(payment);
        if (events.findByEventKey(eventKey).isPresent()) {
            return pendingRefund(attempt, null);
        }
        PaymentEvent event = events.save(new PaymentEvent(
                attempt,
                payment.id(),
                requestId,
                eventKey,
                payment.status(),
                payment.statusDetail(),
                sha256(notificationPayload),
                payment.payloadHash()));

        String providerStatus = payment.status().toLowerCase(Locale.ROOT);
        switch (providerStatus) {
            case "pending", "in_process", "in_mediation", "authorized" -> {
                attempt.providerStatus(payment.id(), providerStatus, PaymentAttemptStatus.PENDING);
                event.processed("PENDING");
            }
            case "rejected", "cancelled" -> {
                attempt.providerStatus(payment.id(), providerStatus, PaymentAttemptStatus.REJECTED);
                event.processed("REJECTED");
            }
            case "approved" -> {
                Optional<RefundInstruction> refund = processApproval(attempt, event, payment, order);
                if (refund.isPresent()) {
                    return refund;
                }
            }
            case "refunded", "charged_back" -> {
                attempt.providerStatus(payment.id(), providerStatus, PaymentAttemptStatus.REFUNDED);
                order.markPaymentRefunded();
                event.processed("REFUNDED_BY_PROVIDER");
            }
            default -> event.processed("IGNORED_STATUS");
        }
        return Optional.empty();
    }

    @Transactional
    public void refundCompleted(RefundInstruction instruction, String refundId) {
        PaymentAttempt attempt = attemptForUpdate(instruction.attemptId());
        if (attempt.getStatus() == PaymentAttemptStatus.REFUNDED) {
            return;
        }
        if (attempt.getStatus() != PaymentAttemptStatus.REFUND_PENDING
                || !instruction.idempotencyKey().equals(attempt.getRefundIdempotencyKey())
                || !instruction.paymentId().equals(attempt.getProviderPaymentId())) {
            throw new IllegalStateException("Refund completion does not match the pending refund.");
        }
        var order = orders.findByIdForUpdate(attempt.getOrder().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found."));
        attempt.refundCompleted(refundId);
        order.markPaymentRefunded();
        event(instruction.eventId()).ifPresent(value -> value.processed("REFUNDED"));
    }

    @Transactional
    public void refundFailed(RefundInstruction instruction, String error) {
        PaymentAttempt attempt = attemptForUpdate(instruction.attemptId());
        if (attempt.getStatus() == PaymentAttemptStatus.REFUND_PENDING) {
            attempt.refundFailed(error);
            event(instruction.eventId()).ifPresent(value -> value.processed("REFUND_FAILED"));
        }
    }

    @Transactional(readOnly = true)
    public List<RefundInstruction> pendingRefunds() {
        return attempts.findTop50ByStatusOrderByUpdatedAtAsc(PaymentAttemptStatus.REFUND_PENDING).stream()
                .filter(attempt -> attempt.getProviderPaymentId() != null
                        && attempt.getRefundIdempotencyKey() != null)
                .map(attempt -> new RefundInstruction(
                        attempt.getPublicId(),
                        attempt.getProviderPaymentId(),
                        attempt.getRefundIdempotencyKey(),
                        null))
                .toList();
    }

    private Optional<RefundInstruction> processApproval(
            PaymentAttempt attempt,
            PaymentEvent event,
            ProviderPayment payment,
            com.computerstore.order.domain.CustomerOrder order
    ) {
        if (attempt.getStatus() == PaymentAttemptStatus.REFUND_PENDING) {
            event.processed("REFUND_PENDING");
            return pendingRefund(attempt, event.getId());
        }
        if (attempt.getStatus() == PaymentAttemptStatus.APPROVED) {
            event.processed("ALREADY_APPROVED");
            return Optional.empty();
        }
        boolean approvedBeforeExpiry = payment.approvedAt() != null
                && payment.approvedAt().isBefore(attempt.getExpiresAt());
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT && approvedBeforeExpiry) {
            order.approveMercadoPagoPayment();
            attempt.providerStatus(payment.id(), payment.status(), PaymentAttemptStatus.APPROVED);
            event.processed("ORDER_PAID");
            return Optional.empty();
        }

        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            stock.release(order);
            order.expire();
        }
        UUID refundKey = attempt.requestRefund(payment.id(), payment.status());
        order.markPaymentRefundPending();
        event.processed("REFUND_PENDING");
        return Optional.of(new RefundInstruction(attempt.getPublicId(), payment.id(), refundKey, event.getId()));
    }

    private Optional<RefundInstruction> pendingRefund(PaymentAttempt attempt, Long eventId) {
        if (attempt.getStatus() != PaymentAttemptStatus.REFUND_PENDING) {
            return Optional.empty();
        }
        return Optional.of(new RefundInstruction(
                attempt.getPublicId(),
                attempt.getProviderPaymentId(),
                attempt.getRefundIdempotencyKey(),
                eventId));
    }

    private void validateAuthoritativePayment(PaymentAttempt attempt, ProviderPayment payment) {
        if (!attempt.getPublicId().toString().equals(payment.externalReference())
                || attempt.getPreferenceId() == null
                || !attempt.getPreferenceId().equals(payment.preferenceId())
                || !properties.collectorId().equals(payment.collectorId())
                || attempt.getAmount().compareTo(payment.amount()) != 0
                || !"ARS".equals(payment.currency())
                || !attempt.getCurrency().equals(payment.currency())) {
            throw new InvalidRequestException("Mercado Pago payment data does not match the payment attempt.");
        }
    }

    private PaymentPreferenceRequest preferenceRequest(PaymentAttempt attempt) {
        return new PaymentPreferenceRequest(
                attempt.getPublicId(),
                attempt.getOrder().getId(),
                attempt.getAmount(),
                attempt.getCurrency(),
                attempt.getExpiresAt(),
                attempt.getOrder().getItems().stream()
                        .map(item -> new PaymentPreferenceRequest.Item(
                                item.getVariant().getId().toString(),
                                item.getProductName() + " - " + item.getVariantColorName(),
                                item.getQuantity(),
                                item.getUnitPrice()))
                        .toList());
    }

    private PaymentCheckoutResponse response(PaymentAttempt attempt) {
        return new PaymentCheckoutResponse(
                attempt.getPublicId(),
                attempt.getOrder().getId(),
                attempt.getStatus().name(),
                attempt.getCheckoutUrl(),
                attempt.getExpiresAt());
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
        String canonical = String.join("|",
                payment.id(),
                payment.status(),
                nullToEmpty(payment.statusDetail()),
                payment.lastUpdatedAt() == null ? "" : payment.lastUpdatedAt().toString());
        return sha256(canonical);
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
