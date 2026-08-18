package com.computerstore.payment.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "payment_events")
public class PaymentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false)
    private PaymentAttempt attempt;

    @Column(name = "provider_payment_id", nullable = false, length = 100)
    private String providerPaymentId;

    @Column(name = "request_id", nullable = false, length = 100)
    private String requestId;

    @Column(name = "event_key", nullable = false, unique = true, length = 64)
    private String eventKey;

    @Column(name = "provider_status", nullable = false, length = 50)
    private String providerStatus;

    @Column(name = "provider_status_detail", length = 100)
    private String providerStatusDetail;

    @Column(name = "notification_payload_hash", nullable = false, length = 64)
    private String notificationPayloadHash;

    @Column(name = "provider_payload_hash", nullable = false, length = 64)
    private String providerPayloadHash;

    @Column(nullable = false, length = 50)
    private String outcome;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected PaymentEvent() {
    }

    public PaymentEvent(
            PaymentAttempt attempt,
            String providerPaymentId,
            String requestId,
            String eventKey,
            String providerStatus,
            String providerStatusDetail,
            String notificationPayloadHash,
            String providerPayloadHash
    ) {
        this.attempt = attempt;
        this.providerPaymentId = providerPaymentId;
        this.requestId = requestId;
        this.eventKey = eventKey;
        this.providerStatus = providerStatus;
        this.providerStatusDetail = providerStatusDetail;
        this.notificationPayloadHash = notificationPayloadHash;
        this.providerPayloadHash = providerPayloadHash;
        this.outcome = "RECEIVED";
    }

    @PrePersist
    void received() {
        receivedAt = Instant.now();
    }

    public void processed(String outcome) {
        this.outcome = outcome;
        this.processedAt = Instant.now();
    }

    public Long getId() { return id; }
}
