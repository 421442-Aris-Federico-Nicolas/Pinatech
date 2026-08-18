package com.computerstore.payment.gateway;

import java.math.BigDecimal;
import java.time.Instant;

public record ProviderPayment(
        String id,
        String externalReference,
        String preferenceId,
        String collectorId,
        BigDecimal amount,
        String currency,
        String status,
        String statusDetail,
        Instant approvedAt,
        Instant lastUpdatedAt,
        boolean liveMode,
        String operationType,
        BigDecimal amountRefunded,
        String payloadHash
) {
}
