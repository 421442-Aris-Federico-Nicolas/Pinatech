package com.computerstore.payment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.computerstore.order.domain.CustomerOrder;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "payment_attempts")
public class PaymentAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private CustomerOrder order;

    @Column(nullable = false, length = 30)
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentAttemptStatus status;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "preference_id", length = 100)
    private String preferenceId;

    @Column(name = "provider_payment_id", length = 100)
    private String providerPaymentId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "checkout_url")
    private String checkoutUrl;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "refund_idempotency_key")
    private UUID refundIdempotencyKey;

    @Column(name = "refund_id", length = 100)
    private String refundId;

    @Column(name = "last_provider_status", length = 50)
    private String lastProviderStatus;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PaymentAttempt() {
    }

    public PaymentAttempt(CustomerOrder order, String idempotencyKey) {
        this.publicId = UUID.randomUUID();
        this.order = order;
        this.provider = "MERCADO_PAGO";
        this.status = PaymentAttemptStatus.CREATED;
        this.idempotencyKey = idempotencyKey;
        this.amount = order.getTotal();
        this.currency = order.getCurrency();
        this.expiresAt = order.getReservationExpiresAt();
    }

    @PrePersist
    void created() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void updated() {
        updatedAt = Instant.now();
    }

    public void preferenceCreated(String preferenceId, String checkoutUrl) {
        if (status != PaymentAttemptStatus.CREATED && status != PaymentAttemptStatus.PREFERENCE_CREATED) {
            return;
        }
        this.preferenceId = preferenceId;
        this.checkoutUrl = checkoutUrl;
        this.status = PaymentAttemptStatus.PREFERENCE_CREATED;
        this.lastError = null;
    }

    public void preferenceFailed(String error) {
        this.lastError = truncate(error);
    }

    public void providerStatus(String paymentId, String providerStatus, PaymentAttemptStatus target) {
        if (providerPaymentId != null && !providerPaymentId.equals(paymentId) && rank(target) <= rank(status)) {
            lastProviderStatus = providerStatus;
            return;
        }
        providerPaymentId = paymentId;
        lastProviderStatus = providerStatus;
        if (rank(target) >= rank(status)) {
            status = target;
        }
        lastError = null;
    }

    public UUID requestRefund(String paymentId, String providerStatus) {
        providerStatus(paymentId, providerStatus, PaymentAttemptStatus.REFUND_PENDING);
        if (refundIdempotencyKey == null) {
            refundIdempotencyKey = UUID.randomUUID();
        }
        return refundIdempotencyKey;
    }

    public void refundCompleted(String refundId) {
        this.refundId = refundId;
        this.status = PaymentAttemptStatus.REFUNDED;
        this.lastError = null;
    }

    public void refundFailed(String error) {
        status = PaymentAttemptStatus.REFUND_PENDING;
        lastError = truncate(error);
    }

    private int rank(PaymentAttemptStatus value) {
        return switch (value) {
            case CREATED -> 0;
            case PREFERENCE_CREATED -> 1;
            case PENDING -> 2;
            case REJECTED -> 3;
            case APPROVED -> 4;
            case REFUND_PENDING -> 5;
            case REFUNDED -> 6;
        };
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    public Long getId() { return id; }
    public UUID getPublicId() { return publicId; }
    public CustomerOrder getOrder() { return order; }
    public PaymentAttemptStatus getStatus() { return status; }
    public String getPreferenceId() { return preferenceId; }
    public String getProviderPaymentId() { return providerPaymentId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getCheckoutUrl() { return checkoutUrl; }
    public Instant getExpiresAt() { return expiresAt; }
    public UUID getRefundIdempotencyKey() { return refundIdempotencyKey; }
}
