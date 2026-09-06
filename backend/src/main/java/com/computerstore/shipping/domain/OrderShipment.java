package com.computerstore.shipping.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.computerstore.order.domain.CustomerOrder;
import com.computerstore.order.domain.OrderCancellationReason;
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
    @Column(name = "tracking_sync_pending", nullable = false) private boolean trackingSyncPending;
    @Enumerated(EnumType.STRING) @Column(name = "cancellation_scope", length = 20) private ShipmentCancellationScope cancellationScope;
    @Enumerated(EnumType.STRING) @Column(name = "cancellation_reason", length = 40) private OrderCancellationReason cancellationReason;
    @Column(name = "cancellation_internal_detail", length = 500) private String cancellationInternalDetail;
    @Column(name = "cancellation_requested_by_user_id") private Long cancellationRequestedByUserId;
    @Column(name = "cancellation_requested_at") private Instant cancellationRequestedAt;
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
        if (status == OrderShipmentStatus.CANCELLED && !isCancellation(provider.status())) return false;
        if (providerUpdatedAt != null && provider.updatedAt().isBefore(providerUpdatedAt)) return false;
        providerShipmentId = provider.id(); rawStatus = truncate(provider.status(), 100); rawSubstatus = truncate(provider.substatus(), 100);
        carrierTrackingId = truncate(provider.carrierTrackingId(), 200); trackingUrl = truncate(provider.trackingUrl(), 2048);
        estimatedDeliveryAt = provider.estimatedDelivery();
        providerUpdatedAt = provider.updatedAt(); nextAttemptAt = now; updatedAt = now;
        if (isDamage(provider.status(), provider.substatus())) incident = true;
        if (isCancellation(provider.status())) {
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
    public void trackingSyncResult(boolean complete) { trackingSyncPending = !complete; }
    public void retryNow(Instant now) { if (providerShipmentId == null) { status = OrderShipmentStatus.RETRY; attemptCount = 0; } nextAttemptAt = now;
        leaseUntil = null; leaseToken = null; lastError = null; }
    public void blockForPayment(Instant now) { status = OrderShipmentStatus.BLOCKED_PAYMENT; nextAttemptAt = now;
        leaseUntil = null; leaseToken = null; lastError = "Shipment paused because payment is not approved."; updatedAt = now; }
    public void paymentNotApproved(Instant now) {
        if (status == OrderShipmentStatus.CANCELLED) return;
        if (status == OrderShipmentStatus.CREATING) return;
        if (providerShipmentId == null) blockForPayment(now);
        else {
            incident = true; status = OrderShipmentStatus.INCIDENT;
            leaseUntil = null; leaseToken = null; lastError = "Provider shipment exists without an approved payment."; updatedAt = now;
        }
    }
    public void cancelled(Instant now) { status = OrderShipmentStatus.CANCELLED; incident = true; rawStatus = "cancelled";
        leaseUntil = null; leaseToken = null; nextAttemptAt = now;
        lastError = "Provider shipment cancelled; the order requires operational resolution."; updatedAt = now; }
    public void requestCancellation(ShipmentCancellationScope scope, OrderCancellationReason reason,
                                    String internalDetail, Long adminId, Instant now) {
        if (scope == ShipmentCancellationScope.ORDER && reason == null) {
            throw new IllegalArgumentException("Order cancellation requires a reason.");
        }
        cancellationScope = scope; cancellationReason = reason;
        cancellationInternalDetail = truncate(internalDetail, 500);
        cancellationRequestedByUserId = adminId; cancellationRequestedAt = now;
        nextAttemptAt = now.plusSeconds(60); lastError = null; updatedAt = now;
    }
    public void cancellationPending(Instant now, String error) {
        incident = true; status = OrderShipmentStatus.INCIDENT;
        nextAttemptAt = now.plusSeconds(120);
        lastError = truncate(error == null ? "Provider shipment cancellation is pending." : error); updatedAt = now;
    }
    public void cancellationFailed(UUID token, Instant now, String error) {
        requireLease(token); leaseUntil = null; leaseToken = null; cancellationPending(now, error);
    }
    public void cancellationCompleted(UUID token, Instant now) {
        requireLease(token);
        cancelled(now);
    }
    public void cancellationRejected(UUID token, Instant now, String error) {
        requireLease(token);
        cancellationScope = null; cancellationReason = null; cancellationInternalDetail = null;
        cancellationRequestedByUserId = null; cancellationRequestedAt = null;
        leaseUntil = null; leaseToken = null; nextAttemptAt = now; lastError = truncate(error);
        incident = isDamage(rawStatus, rawSubstatus);
        if (isCancellation(rawStatus)) status = OrderShipmentStatus.CANCELLED;
        else if (incident) status = OrderShipmentStatus.INCIDENT;
        else if ("delivered".equalsIgnoreCase(rawStatus)) status = OrderShipmentStatus.DELIVERED;
        else status = OrderShipmentStatus.ACTIVE;
        updatedAt = now;
    }
    public void replaceCancelled(String source, Instant now) {
        if (status != OrderShipmentStatus.CANCELLED || providerShipmentId == null) {
            throw new IllegalStateException("Only a cancelled provider shipment can be replaced.");
        }
        externalId = externalId(source + "|replacement|" + providerShipmentId, order.getId());
        status = OrderShipmentStatus.PENDING_CREATE; providerShipmentId = null; rawStatus = null; rawSubstatus = null;
        carrierTrackingId = null; trackingUrl = null; estimatedDeliveryAt = null; providerUpdatedAt = null;
        incident = false; trackingSyncPending = false; attemptCount = 0; nextAttemptAt = now; leaseUntil = null; leaseToken = null;
        cancellationScope = null; cancellationReason = null; cancellationInternalDetail = null;
        cancellationRequestedByUserId = null; cancellationRequestedAt = null;
        lastError = null; updatedAt = now;
    }
    private void requireLease(UUID token) { if (token == null || !token.equals(leaseToken)) throw new IllegalStateException("Stale shipping lease."); }
    private boolean isDamage(String value, String substatus) { return "delivered_with_damage".equalsIgnoreCase(value)
            || "delivered_with_damage".equalsIgnoreCase(substatus); }
    private boolean isCancellation(String value) { return "cancelled".equalsIgnoreCase(value) || "canceled".equalsIgnoreCase(value); }
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
    public boolean isTrackingSyncPending() { return trackingSyncPending; }
    public ShipmentCancellationScope getCancellationScope() { return cancellationScope; }
    public OrderCancellationReason getCancellationReason() { return cancellationReason; }
    public String getCancellationInternalDetail() { return cancellationInternalDetail; }
    public Long getCancellationRequestedByUserId() { return cancellationRequestedByUserId; }
    public Instant getCancellationRequestedAt() { return cancellationRequestedAt; }
    public boolean isCancellationRequested() { return cancellationScope != null; }
    public boolean matchesCancellation(ShipmentCancellationScope scope, OrderCancellationReason reason,
                                       String internalDetail) {
        return cancellationScope == scope && cancellationReason == reason
                && Objects.equals(cancellationInternalDetail, truncate(internalDetail, 500));
    }
    public Instant getProviderUpdatedAt() { return providerUpdatedAt; }
    public Instant getEstimatedDeliveryAt() { return estimatedDeliveryAt; }
}
