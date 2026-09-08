package com.computerstore.shipping.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

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

    @Test
    void boundsAndExpiresGuestAndIpBuckets() {
        MutableClock clock = new MutableClock();
        var limiter = new ShippingQuoteRateLimiter(2, 1_000, 100, clock);
        for (int index = 0; index < 100; index++) limiter.check("ip-" + index);

        assertThrows(RateLimitExceededException.class, () -> limiter.check("overflow"));
        assertEquals(100, limiter.bucketCount());
        clock.advance(java.time.Duration.ofSeconds(2));
        assertDoesNotThrow(() -> limiter.check("fresh"));
        assertEquals(1, limiter.bucketCount());
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-09-08T10:00:00Z");
        void advance(java.time.Duration duration) { now = now.plus(duration); }
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
