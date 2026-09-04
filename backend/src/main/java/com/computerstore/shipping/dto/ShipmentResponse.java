package com.computerstore.shipping.dto;

import java.time.Instant;
import java.util.List;

public record ShipmentResponse(String status, String providerStatus, String providerSubstatus,
        String carrier, String trackingCode, String trackingUrl, Instant estimatedDeliveryAt,
        boolean incident, List<Event> history) {
    public record Event(String status, String substatus, Instant occurredAt) {}
}
