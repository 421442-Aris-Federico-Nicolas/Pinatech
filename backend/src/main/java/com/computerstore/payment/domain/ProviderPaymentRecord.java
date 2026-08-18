package com.computerstore.payment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import com.computerstore.payment.gateway.ProviderPayment;
import com.computerstore.payment.gateway.RefundResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "provider_payments")
public class ProviderPaymentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false)
    private PaymentAttempt attempt;

    @Column(name = "provider_payment_id", nullable = false, unique = true, length = 100)
    private String providerPaymentId;

    @Column(name = "provider_status", nullable = false, length = 50)
    private String providerStatus;

    @Column(name = "provider_status_detail", length = 100)
    private String providerStatusDetail;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "provider_updated_at")
    private Instant providerUpdatedAt;

    @Column(name = "funds_order", nullable = false)
    private boolean fundsOrder;

    @Column(name = "live_mode", nullable = false)
    private boolean liveMode;

    @Column(name = "operation_type", nullable = false, length = 50)
    private String operationType;

    @Column(name = "amount_refunded", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountRefunded;

    @Column(name = "dispute_status", length = 30)
    private String disputeStatus;

    @Column(name = "refund_idempotency_key")
    private UUID refundIdempotencyKey;

    @Column(name = "refund_id", length = 100)
    private String refundId;

    @Column(name = "refund_status", length = 30)
    private String refundStatus;

    @Column(name = "refund_amount", precision = 19, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "refund_last_error", length = 500)
    private String refundLastError;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProviderPaymentRecord() {
    }

    public ProviderPaymentRecord(PaymentAttempt attempt, ProviderPayment payment) {
        this.attempt = attempt;
        this.providerPaymentId = payment.id();
        update(payment);
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

    public void update(ProviderPayment payment) {
        providerStatus = payment.status().toLowerCase();
        providerStatusDetail = payment.statusDetail();
        amount = payment.amount();
        currency = payment.currency();
        approvedAt = payment.approvedAt();
        providerUpdatedAt = payment.lastUpdatedAt();
        liveMode = payment.liveMode();
        operationType = payment.operationType();
        amountRefunded = payment.amountRefunded();
    }

    public boolean isStale(ProviderPayment payment) {
        return providerUpdatedAt != null
                && (payment.lastUpdatedAt() == null || payment.lastUpdatedAt().isBefore(providerUpdatedAt));
    }

    public void fundsOrder() {
        fundsOrder = true;
    }

    public UUID requestRefund(Instant now) {
        if (refundIdempotencyKey == null) {
            refundIdempotencyKey = UUID.randomUUID();
        }
        refundStatus = "PENDING";
        refundAmount = amount;
        refundLastError = null;
        nextRetryAt = now;
        return refundIdempotencyKey;
    }

    public void refundResult(RefundResult result, Instant now) {
        refundId = result.id();
        String resultStatus = normalizeRefundStatus(result.status());
        refundStatus = resultStatus;
        refundAmount = result.amount();
        refundLastError = null;
        leaseUntil = null;
        if ("APPROVED".equals(resultStatus) && amount.compareTo(result.amount()) == 0) {
            amountRefunded = result.amount();
            nextRetryAt = null;
        } else {
            if ("APPROVED".equals(resultStatus)) {
                refundStatus = "PENDING";
                refundLastError = "Approved refund amount does not match the expected amount.";
            }
            nextRetryAt = retryAt(now);
        }
    }

    public boolean refundTerminalAndComplete() {
        return "APPROVED".equals(refundStatus)
                && refundAmount != null
                && amount.compareTo(refundAmount) == 0;
    }

    public void refundFailed(String error, Instant now) {
        refundStatus = "PENDING";
        refundLastError = truncate(error);
        attemptCount++;
        leaseUntil = null;
        nextRetryAt = retryAt(now);
    }

    public void lease(Instant until) {
        leaseUntil = until;
    }

    public void mediation() {
        disputeStatus = "MEDIATION";
    }

    public void chargeback() {
        disputeStatus = "CHARGEBACK";
    }

    public void externallyRefunded() {
        refundStatus = "APPROVED";
        refundAmount = amountRefunded;
        nextRetryAt = null;
        leaseUntil = null;
    }

    private Instant retryAt(Instant now) {
        attemptCount++;
        long seconds = Math.min(3600, 30L << Math.min(attemptCount - 1, 7));
        return now.plusSeconds(seconds);
    }

    private String normalizeRefundStatus(String status) {
        if (status == null) return "PENDING";
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "approved" -> "APPROVED";
            case "rejected", "cancelled" -> "REJECTED";
            case "pending", "in_process" -> "PENDING";
            default -> "PENDING";
        };
    }

    private String truncate(String value) {
        if (value == null) return null;
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    public Long getId() { return id; }
    public PaymentAttempt getAttempt() { return attempt; }
    public String getProviderPaymentId() { return providerPaymentId; }
    public String getProviderStatus() { return providerStatus; }
    public BigDecimal getAmount() { return amount; }
    public boolean isFundsOrder() { return fundsOrder; }
    public UUID getRefundIdempotencyKey() { return refundIdempotencyKey; }
    public String getRefundId() { return refundId; }
    public String getRefundStatus() { return refundStatus; }
    public BigDecimal getRefundAmount() { return refundAmount; }
}
