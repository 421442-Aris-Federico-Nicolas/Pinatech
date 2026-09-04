package com.computerstore.shipping.domain;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@Entity @Table(name = "shipping_webhook_inbox")
public class ShippingWebhookInbox {
    public enum Status { PENDING, PROCESSING, DONE, FAILED }
    @Id private UUID id;
    @Column(name = "provider_shipment_id", nullable = false) private long providerShipmentId;
    @Column(name = "payload_hash", nullable = false, length = 64) private String payloadHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Column(name = "attempt_count", nullable = false) private int attemptCount;
    @Column(name = "next_attempt_at", nullable = false) private Instant nextAttemptAt;
    @Column(name = "lease_until") private Instant leaseUntil;
    @Column(name = "lease_token") private UUID leaseToken;
    @Column(name = "last_error", length = 500) private String lastError;
    @Column(name = "received_at", nullable = false) private Instant receivedAt;
    @Column(name = "processed_at") private Instant processedAt;
    protected ShippingWebhookInbox() {}
    public ShippingWebhookInbox(long providerId, String hash, Instant now) { id = UUID.randomUUID();
        providerShipmentId = providerId; payloadHash = hash; status = Status.PENDING; nextAttemptAt = now; receivedAt = now; }
    public UUID lease(Instant now) { status = Status.PROCESSING; leaseUntil = now.plusSeconds(90);
        leaseToken = UUID.randomUUID(); return leaseToken; }
    public void done(UUID token, Instant now) { requireLease(token); status = Status.DONE; processedAt = now;
        leaseUntil = null; leaseToken = null; lastError = null; }
    public void failed(UUID token, Instant now, String error) { requireLease(token); attemptCount++;
        status = attemptCount >= 10 ? Status.FAILED : Status.PENDING; nextAttemptAt = now.plusSeconds(Math.min(3600, 30L << Math.min(attemptCount - 1, 7)));
        processedAt = status == Status.FAILED ? now : null;
        leaseUntil = null; leaseToken = null; lastError = error == null ? null : error.substring(0, Math.min(500, error.length())); }
    private void requireLease(UUID token) { if (token == null || !token.equals(leaseToken)) throw new IllegalStateException("Stale webhook lease."); }
    public UUID getId() { return id; } public long getProviderShipmentId() { return providerShipmentId; }
}
