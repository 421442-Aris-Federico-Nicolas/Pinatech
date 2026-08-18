package com.computerstore.payment.dto;

import java.time.Instant;
import java.util.UUID;

public record PaymentCheckoutResponse(
        UUID attemptId,
        Long orderId,
        String status,
        String checkoutUrl,
        Instant expiresAt
) {
}
