package com.computerstore.shipping.service;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.computerstore.common.exception.RateLimitExceededException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ShippingQuoteRateLimiter {
    private final ConcurrentHashMap<Object, Window> windows = new ConcurrentHashMap<>();
    private final int limit;
    private final long windowMs;
    private final Clock clock;
    private final int maxBuckets;
    private final AtomicLong checks = new AtomicLong();

    @org.springframework.beans.factory.annotation.Autowired
    public ShippingQuoteRateLimiter(
            @Value("${app.shipping.quote-rate-limit.max-requests:20}") int limit,
            @Value("${app.shipping.quote-rate-limit.window-ms:60000}") long windowMs,
            @Value("${app.shipping.quote-rate-limit.max-buckets:10000}") int maxBuckets,
            Clock clock) {
        if (limit <= 0 || windowMs <= 0 || maxBuckets < 100) {
            throw new IllegalArgumentException("Shipping quote rate limits must be positive and bounded.");
        }
        this.limit = limit;
        this.windowMs = windowMs;
        this.maxBuckets = maxBuckets;
        this.clock = clock;
    }

    public ShippingQuoteRateLimiter(int limit, long windowMs, Clock clock) {
        this(limit, windowMs, 10000, clock);
    }

    public void check(Long userId) {
        checkOwner(userId);
    }

    public void check(java.util.UUID guestSessionId) {
        checkOwner(guestSessionId);
    }

    public void check(String clientAddress) {
        checkOwner(clientAddress);
    }

    private void checkOwner(Object owner) {
        long now = clock.millis();
        if ((checks.incrementAndGet() & 255) == 0 || windows.size() >= maxBuckets) {
            windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt() >= windowMs);
        }
        if (windows.size() >= maxBuckets && !windows.containsKey(owner)) {
            throw new RateLimitExceededException("Too many shipping quote requests. Please try again later.");
        }
        Window window = windows.compute(owner, (ignored, existing) ->
                existing == null || now - existing.startedAt() >= windowMs
                        ? new Window(now, 1) : new Window(existing.startedAt(), existing.requests() + 1));
        if (window.requests() > limit) {
            throw new RateLimitExceededException("Too many shipping quote requests. Please try again later.");
        }
    }

    private record Window(long startedAt, int requests) {}
    int bucketCount() { return windows.size(); }
}
