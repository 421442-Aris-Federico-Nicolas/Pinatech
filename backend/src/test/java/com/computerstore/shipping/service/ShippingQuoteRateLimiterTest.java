package com.computerstore.shipping.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import com.computerstore.common.exception.RateLimitExceededException;
import org.junit.jupiter.api.Test;

class ShippingQuoteRateLimiterTest {
    @Test
    void limitsEachCustomerIndependently() {
        var limiter = new ShippingQuoteRateLimiter(2, 60_000,
                Clock.fixed(Instant.parse("2026-09-04T12:00:00Z"), ZoneOffset.UTC));

        assertDoesNotThrow(() -> limiter.check(1L));
        assertDoesNotThrow(() -> limiter.check(1L));
        assertThrows(RateLimitExceededException.class, () -> limiter.check(1L));
        assertDoesNotThrow(() -> limiter.check(2L));
    }
}
