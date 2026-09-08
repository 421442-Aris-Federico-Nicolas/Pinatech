package com.computerstore.auth.service;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.computerstore.common.exception.RateLimitExceededException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AuthRateLimiter {

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int maxLoginAttempts;
    private final int maxRegistrationAttempts;
    private final int maxRefreshAttempts;
    private final int maxAccountActionAttempts;
    private final long windowMs;
    private final Clock clock;
    private final int maxBuckets;
    private final AtomicLong checks = new AtomicLong();

    @Autowired
    public AuthRateLimiter(
            @Value("${app.auth-rate-limit.max-login-attempts}") int maxLoginAttempts,
            @Value("${app.auth-rate-limit.max-registration-attempts}") int maxRegistrationAttempts,
            @Value("${app.auth-rate-limit.max-refresh-attempts}") int maxRefreshAttempts,
            @Value("${app.auth-rate-limit.max-account-action-attempts:5}") int maxAccountActionAttempts,
            @Value("${app.auth-rate-limit.window-ms}") long windowMs,
            @Value("${app.auth-rate-limit.max-buckets:10000}") int maxBuckets
    ) {
        this(maxLoginAttempts, maxRegistrationAttempts, maxRefreshAttempts,
                maxAccountActionAttempts, windowMs, maxBuckets, Clock.systemUTC());
    }

    AuthRateLimiter(int maxLoginAttempts, int maxRefreshAttempts, long windowMs, Clock clock) {
        this(maxLoginAttempts, 3, maxRefreshAttempts, 5, windowMs, 10000, clock);
    }

    AuthRateLimiter(int maxLoginAttempts, int maxRegistrationAttempts, int maxRefreshAttempts, long windowMs, Clock clock) {
        this(maxLoginAttempts, maxRegistrationAttempts, maxRefreshAttempts, 5, windowMs, 10000, clock);
    }

    AuthRateLimiter(int maxLoginAttempts, int maxRegistrationAttempts, int maxRefreshAttempts,
                    int maxAccountActionAttempts, long windowMs, Clock clock) {
        this(maxLoginAttempts, maxRegistrationAttempts, maxRefreshAttempts,
                maxAccountActionAttempts, windowMs, 10000, clock);
    }

    AuthRateLimiter(int maxLoginAttempts, int maxRegistrationAttempts, int maxRefreshAttempts,
                    int maxAccountActionAttempts, long windowMs, int maxBuckets, Clock clock) {
        if (maxBuckets < 100) throw new IllegalArgumentException("Auth rate-limit max buckets must be at least 100.");
        this.maxLoginAttempts = maxLoginAttempts;
        this.maxRegistrationAttempts = maxRegistrationAttempts;
        this.maxRefreshAttempts = maxRefreshAttempts;
        this.maxAccountActionAttempts = maxAccountActionAttempts;
        this.windowMs = windowMs;
        this.maxBuckets = maxBuckets;
        this.clock = clock;
    }

    public void checkLogin(String clientAddress, String email) {
        check("login:" + clientAddress + ':' + email.trim().toLowerCase(), maxLoginAttempts);
    }

    public void checkRefresh(String clientAddress) {
        check("refresh:" + clientAddress, maxRefreshAttempts);
    }

    public void checkRegistration(String clientAddress) {
        check("register:" + clientAddress, maxRegistrationAttempts);
    }

    public void checkAccountAction(String clientAddress, String action, String subject) {
        check("account:" + action + ':' + clientAddress + ':' + Integer.toHexString(subject.hashCode()),
                maxAccountActionAttempts);
    }

    public void resetLogin(String clientAddress, String email) {
        windows.remove("login:" + clientAddress + ':' + email.trim().toLowerCase());
    }

    private void check(String key, int limit) {
        long now = clock.millis();
        if ((checks.incrementAndGet() & 255) == 0 || windows.size() >= maxBuckets) {
            windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt >= windowMs);
        }
        if (windows.size() >= maxBuckets && !windows.containsKey(key)) {
            throw new RateLimitExceededException("Too many authentication attempts. Please try again later.");
        }
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

    int bucketCount() { return windows.size(); }
}
