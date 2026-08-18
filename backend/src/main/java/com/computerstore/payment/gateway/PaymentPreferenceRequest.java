package com.computerstore.payment.gateway;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PaymentPreferenceRequest(
        UUID attemptId,
        Long orderId,
        BigDecimal amount,
        String currency,
        Instant expiresAt,
        List<Item> items
) {
    public record Item(String id, String title, int quantity, BigDecimal unitPrice) {
    }
}
