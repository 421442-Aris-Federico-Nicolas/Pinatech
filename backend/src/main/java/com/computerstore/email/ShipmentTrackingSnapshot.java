package com.computerstore.email;

import java.time.Instant;

public record ShipmentTrackingSnapshot(String carrier, String code, Instant estimatedDeliveryAt, String trackingUrl) {}
