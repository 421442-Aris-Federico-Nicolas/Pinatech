package com.computerstore.auth.service;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;

import com.computerstore.common.exception.RateLimitExceededException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AuthRateLimiter {

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int maxLoginAttempts;
    private final int maxRefreshAttempts;
    private final long windowMs;
    private final Clock clock;

    public AuthRateLimiter(
            @Value("${app.auth-rate-limit.max-login-attempts}") int maxLoginAttempts,
            @Value("${app.auth-rate-limit.max-refresh-attempts}") int maxRefreshAttempts,
            @Value("${app.auth-rate-limit.window-ms}") long windowMs
    ) {
        this(maxLoginAttempts, maxRefreshAttempts, windowMs, Clock.systemUTC());
    }

    AuthRateLimiter(int maxLoginAttempts, int maxRefreshAttempts, long windowMs, Clock clock) {
        this.maxLoginAttempts = maxLoginAttempts;
        this.maxRefreshAttempts = maxRefreshAttempts;
        this.windowMs = windowMs;
        this.clock = clock;
    }

    public void checkLogin(String clientAddress, String email) {
        check("login:" + clientAddress + ':' + email.trim().toLowerCase(), maxLoginAttempts);
    }

    public void checkRefresh(String clientAddress) {
        check("refresh:" + clientAddress, maxRefreshAttempts);
    }

    public void resetLogin(String clientAddress, String email) {
        windows.remove("login:" + clientAddress + ':' + email.trim().toLowerCase());
    }

    private void check(String key, int limit) {
        long now = clock.millis();
        Window window = windows.compute(key, (ignored, existing) -> {
            if (existing == null || now - existing.startedAt >= windowMs) {
                return new Window(now, 1);
            }
            return new Window(existing.startedAt, existing.attempts + 1);
        });
        if (window.attempts > limit) {
            throw new RateLimitExceededException("Too many authentication attempts. Please try again later.");
        }
    }

    private record Window(long startedAt, int attempts) {
    }
}
