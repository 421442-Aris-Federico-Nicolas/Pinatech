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
        List<Item> items,
        UUID guestOrderPublicId
) {
    public PaymentPreferenceRequest(UUID attemptId, Long orderId, BigDecimal amount, String currency,
                                    Instant expiresAt, List<Item> items) {
        this(attemptId, orderId, amount, currency, expiresAt, items, null);
    }

    public record Item(String id, String title, int quantity, BigDecimal unitPrice) {
    }
}
