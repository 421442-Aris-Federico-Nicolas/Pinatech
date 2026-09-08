package com.computerstore.auth.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.computerstore.common.exception.RateLimitExceededException;
import org.junit.jupiter.api.Test;
import java.time.*;

class AuthRateLimiterTest {

    @Test
    void blocksAttemptsAboveTheConfiguredLoginLimitAndAllowsSuccessfulLoginReset() {
        AuthRateLimiter limiter = new AuthRateLimiter(2, 3, 60_000, java.time.Clock.systemUTC());

        limiter.checkLogin("127.0.0.1", "customer@example.com");
        limiter.checkLogin("127.0.0.1", "customer@example.com");
        assertThrows(RateLimitExceededException.class,
                () -> limiter.checkLogin("127.0.0.1", "customer@example.com"));

        limiter.resetLogin("127.0.0.1", "customer@example.com");
        assertDoesNotThrow(() -> limiter.checkLogin("127.0.0.1", "customer@example.com"));
    }

    @Test
    void limitsRegistrationsByClientAddress() {
        AuthRateLimiter limiter = new AuthRateLimiter(5, 2, 20, 60_000, java.time.Clock.systemUTC());

        limiter.checkRegistration("127.0.0.1");
        limiter.checkRegistration("127.0.0.1");

        assertThrows(RateLimitExceededException.class, () -> limiter.checkRegistration("127.0.0.1"));
        assertDoesNotThrow(() -> limiter.checkRegistration("127.0.0.2"));
    }

    @Test
    void boundsAndExpiresHighCardinalityBuckets() {
        MutableClock clock = new MutableClock();
        AuthRateLimiter limiter = new AuthRateLimiter(5, 5, 5, 5, 1_000, 100, clock);
        for (int index = 0; index < 100; index++) limiter.checkRegistration("ip-" + index);

        assertThrows(RateLimitExceededException.class, () -> limiter.checkRegistration("overflow"));
        assertEquals(100, limiter.bucketCount());
        clock.advance(Duration.ofSeconds(2));
        assertDoesNotThrow(() -> limiter.checkRegistration("fresh"));
        assertEquals(1, limiter.bucketCount());
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-09-08T10:00:00Z");
        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
