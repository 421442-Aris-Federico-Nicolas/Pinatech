package com.computerstore.shipping.domain;

import java.time.Instant;
import jakarta.persistence.*;

@Entity @Table(name = "shipment_events")
public class ShipmentEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "shipment_id") private OrderShipment shipment;
    @Column(name = "event_key", nullable = false, length = 64) private String eventKey;
    @Column(name = "raw_status", nullable = false, length = 100) private String rawStatus;
    @Column(name = "raw_substatus", length = 100) private String rawSubstatus;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    @Column(name = "recorded_at", nullable = false) private Instant recordedAt;
    protected ShipmentEvent() {}
    public ShipmentEvent(OrderShipment shipment, String key, String status, String substatus, Instant occurredAt, Instant now) {
        this.shipment = shipment; this.eventKey = key; this.rawStatus = status; this.rawSubstatus = substatus;
        this.occurredAt = occurredAt; this.recordedAt = now;
    }
    public String getRawStatus() { return rawStatus; } public String getRawSubstatus() { return rawSubstatus; }
    public Instant getOccurredAt() { return occurredAt; }
}
