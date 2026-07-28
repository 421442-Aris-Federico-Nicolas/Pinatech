package com.computerstore.auth.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.computerstore.common.exception.RateLimitExceededException;
import org.junit.jupiter.api.Test;

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
}
