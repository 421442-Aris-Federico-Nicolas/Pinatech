package com.computerstore.shipping.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.computerstore.order.domain.CustomerOrder;
import com.computerstore.shipping.gateway.ZipnovaGateway;
import jakarta.persistence.*;

@Entity
@Table(name = "order_shipments")
public class OrderShipment {
    private static final int MAX_ATTEMPTS = 12;
    @Id private UUID id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "order_id") private CustomerOrder order;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private OrderShipmentStatus status;
    @Column(name = "external_id", nullable = false, length = 30) private String externalId;
    @Column(name = "provider_shipment_id") private Long providerShipmentId;
    @Column(name = "raw_status", length = 100) private String rawStatus;
    @Column(name = "raw_substatus", length = 100) private String rawSubstatus;
    @Column(name = "carrier_tracking_id", length = 200) private String carrierTrackingId;
    @Column(name = "tracking_url", length = 2048) private String trackingUrl;
    @Column(nullable = false) private boolean incident;
    @Column(name = "attempt_count", nullable = false) private int attemptCount;
    @Column(name = "next_attempt_at", nullable = false) private Instant nextAttemptAt;
    @Column(name = "lease_until") private Instant leaseUntil;
    @Column(name = "lease_token") private UUID leaseToken;
    @Column(name = "last_error", length = 500) private String lastError;
    @Column(name = "provider_updated_at") private Instant providerUpdatedAt;
    @Column(name = "estimated_delivery_at") private Instant estimatedDeliveryAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected OrderShipment() {}
    public OrderShipment(CustomerOrder order, String source, Instant now) {
        this.id = UUID.randomUUID(); this.order = order; this.status = OrderShipmentStatus.PENDING_CREATE;
        this.externalId = externalId(source, order.getId()); this.nextAttemptAt = now; this.createdAt = now; this.updatedAt = now;
    }

    public UUID lease(Instant now) { status = OrderShipmentStatus.CREATING; leaseUntil = now.plusSeconds(90);
        leaseToken = UUID.randomUUID(); updatedAt = now; return leaseToken; }
    public UUID leaseReconciliation(Instant now) { leaseUntil = now.plusSeconds(90); leaseToken = UUID.randomUUID();
        updatedAt = now; return leaseToken; }
    public void created(ZipnovaGateway.ProviderShipment provider, UUID token, Instant now) {
        requireLease(token); providerShipmentId = provider.id(); status = OrderShipmentStatus.ACTIVE;
        leaseUntil = null; leaseToken = null; lastError = null; nextAttemptAt = now; update(provider, now);
    }
    public void retry(UUID token, Instant now, Duration retryAfter, String error) {
        requireLease(token); attemptCount++; leaseUntil = null; leaseToken = null;
        status = attemptCount >= MAX_ATTEMPTS ? OrderShipmentStatus.FAILED : OrderShipmentStatus.RETRY;
        long delay = retryAfter == null ? Math.min(21600, 30L << Math.min(attemptCount - 1, 9))
                : Math.min(21600, Math.max(1, retryAfter.toSeconds()));
        nextAttemptAt = now.plusSeconds(delay); lastError = truncate(error); updatedAt = now;
    }
    public void failedPermanently(UUID token, Instant now, String error) { requireLease(token); attemptCount++;
        status = OrderShipmentStatus.FAILED; leaseUntil = null; leaseToken = null; lastError = truncate(error); updatedAt = now; }
    public boolean update(ZipnovaGateway.ProviderShipment provider, Instant now) {
        if (providerShipmentId != null && provider.id() != providerShipmentId) throw new IllegalArgumentException("Shipment mismatch.");
        if (providerUpdatedAt != null && provider.updatedAt().isBefore(providerUpdatedAt)) return false;
        providerShipmentId = provider.id(); rawStatus = truncate(provider.status(), 100); rawSubstatus = truncate(provider.substatus(), 100);
        carrierTrackingId = truncate(provider.carrierTrackingId(), 200); trackingUrl = truncate(provider.trackingUrl(), 2048);
        estimatedDeliveryAt = provider.estimatedDelivery();
        providerUpdatedAt = provider.updatedAt(); nextAttemptAt = now; updatedAt = now;
        if (isDamage(provider.status(), provider.substatus())) incident = true;
        if ("cancelled".equalsIgnoreCase(provider.status())) {
            incident = true;
            status = OrderShipmentStatus.CANCELLED;
        }
        else if (incident) status = OrderShipmentStatus.INCIDENT;
        else if ("delivered".equalsIgnoreCase(provider.status())) status = OrderShipmentStatus.DELIVERED;
        else status = OrderShipmentStatus.ACTIVE;
        return true;
    }
    public void reconciliationDue(UUID token, Instant next) { requireLease(token); this.nextAttemptAt = next;
        this.leaseUntil = null; this.leaseToken = null; }
    public void retryNow(Instant now) { if (providerShipmentId == null) { status = OrderShipmentStatus.RETRY; attemptCount = 0; } nextAttemptAt = now;
        leaseUntil = null; leaseToken = null; lastError = null; }
    public void blockForPayment(Instant now) { status = OrderShipmentStatus.BLOCKED_PAYMENT; nextAttemptAt = now;
        leaseUntil = null; leaseToken = null; lastError = "Shipment paused because payment is not approved."; updatedAt = now; }
    public void paymentNotApproved(Instant now) {
        if (providerShipmentId == null) blockForPayment(now);
        else {
            incident = true; status = OrderShipmentStatus.INCIDENT;
            leaseUntil = null; leaseToken = null; lastError = "Provider shipment exists without an approved payment."; updatedAt = now;
        }
    }
    public void cancelled(Instant now) { status = OrderShipmentStatus.CANCELLED; incident = true;
        lastError = "Provider shipment cancelled; the order requires operational resolution."; updatedAt = now; }
    private void requireLease(UUID token) { if (token == null || !token.equals(leaseToken)) throw new IllegalStateException("Stale shipping lease."); }
    private boolean isDamage(String value, String substatus) { return "delivered_with_damage".equalsIgnoreCase(value)
            || "delivered_with_damage".equalsIgnoreCase(substatus); }
    private String truncate(String value) { return truncate(value, 500); }
    private String truncate(String value, int length) { return value == null ? null : value.substring(0, Math.min(length, value.length())); }
    private String externalId(String source, Long orderId) {
        try {
            String namespace = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(java.nio.charset.StandardCharsets.UTF_8))).substring(0, 8);
            return "P-" + namespace + "-" + orderId;
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is not available.", error);
        }
    }
    public UUID getId() { return id; } public CustomerOrder getOrder() { return order; }
    public OrderShipmentStatus getStatus() { return status; } public String getExternalId() { return externalId; }
    public Long getProviderShipmentId() { return providerShipmentId; } public String getRawStatus() { return rawStatus; }
    public String getRawSubstatus() { return rawSubstatus; } public String getCarrierTrackingId() { return carrierTrackingId; }
    public String getTrackingUrl() { return trackingUrl; } public boolean isIncident() { return incident; }
    public Instant getProviderUpdatedAt() { return providerUpdatedAt; }
    public Instant getEstimatedDeliveryAt() { return estimatedDeliveryAt; }
}
