package com.computerstore.order.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.orders")
public record OrderProperties(Duration reservationTtl, int expirationBatchSize) {

    public OrderProperties {
        if (reservationTtl == null || reservationTtl.isZero() || reservationTtl.isNegative()) {
            throw new IllegalArgumentException("Order reservation TTL must be positive.");
        }
        if (expirationBatchSize < 1) {
            throw new IllegalArgumentException("Order expiration batch size must be positive.");
        }
    }
}
