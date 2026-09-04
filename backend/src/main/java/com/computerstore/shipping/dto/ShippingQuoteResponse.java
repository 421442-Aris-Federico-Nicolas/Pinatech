package com.computerstore.shipping.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ShippingQuoteResponse(List<Option> options) {
    public record Option(UUID shippingQuoteId, String carrier, String serviceCode, String service,
                         String logisticType, BigDecimal amount, String currency,
                         Instant estimatedDeliveryAt, Instant expiresAt, List<String> tags) {}
}
