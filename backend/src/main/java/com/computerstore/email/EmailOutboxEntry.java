package com.computerstore.email;

import com.computerstore.order.domain.CustomerOrder;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_outbox")
public class EmailOutboxEntry {
    static final int MAX_ATTEMPTS = 10;

    @Id
    private UUID id;
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private OrderEmailEventType eventType;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private CustomerOrder order;
    @Column(nullable = false, length = 254)
    private String recipient;
    @Column(name = "customer_name", nullable = false, length = 200)
    private String customerName;
    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmailOutboxStatus status;
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;
    @Column(name = "lease_until")
    private Instant leaseUntil;
    @Column(name = "lease_token")
    private UUID leaseToken;
    @Column(name = "last_error", length = 500)
    private String lastError;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "sent_at")
    private Instant sentAt;

    protected EmailOutboxEntry() {}

    public EmailOutboxEntry(CustomerOrder order, OrderEmailEventType eventType, String reason, Instant now) {
        this.id = UUID.randomUUID();
        this.order = order;
        this.eventType = eventType;
        this.recipient = order.getUser().getEmail();
        this.customerName = order.getUser().getFirstName();
        this.rejectionReason = reason;
        this.status = EmailOutboxStatus.PENDING;
        this.nextAttemptAt = now;
        this.createdAt = now;
    }

    public UUID lease(Instant until) {
        status = EmailOutboxStatus.SENDING;
        leaseUntil = until;
        leaseToken = UUID.randomUUID();
        return leaseToken;
    }

    public void sent(Instant now) {
        status = EmailOutboxStatus.SENT;
        sentAt = now;
        leaseUntil = null;
        leaseToken = null;
        lastError = null;
    }

    public void failed(Instant now, String error) {
        attemptCount++;
        leaseUntil = null;
        leaseToken = null;
        if (attemptCount >= MAX_ATTEMPTS) {
            status = EmailOutboxStatus.FAILED;
        } else {
            status = EmailOutboxStatus.PENDING;
            nextAttemptAt = now.plusSeconds(Math.min(21600, 30L << Math.min(attemptCount - 1, 9)));
        }
        lastError = error == null ? null : error.substring(0, Math.min(500, error.length()));
    }

    public UUID getId() { return id; }
    public OrderEmailEventType getEventType() { return eventType; }
    public CustomerOrder getOrder() { return order; }
    public String getRecipient() { return recipient; }
    public String getCustomerName() { return customerName; }
    public String getRejectionReason() { return rejectionReason; }
    public EmailOutboxStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public boolean hasLease(UUID token) { return token != null && token.equals(leaseToken); }
}
