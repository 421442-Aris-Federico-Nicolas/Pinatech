package com.computerstore.shipping.service;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;

import com.computerstore.common.exception.RateLimitExceededException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ShippingQuoteRateLimiter {
    private final ConcurrentHashMap<Long, Window> windows = new ConcurrentHashMap<>();
    private final int limit;
    private final long windowMs;
    private final Clock clock;

    public ShippingQuoteRateLimiter(
            @Value("${app.shipping.quote-rate-limit.max-requests:20}") int limit,
            @Value("${app.shipping.quote-rate-limit.window-ms:60000}") long windowMs,
            Clock clock) {
        if (limit <= 0 || windowMs <= 0) throw new IllegalArgumentException("Shipping quote rate limits must be positive.");
        this.limit = limit;
        this.windowMs = windowMs;
        this.clock = clock;
    }

    public void check(Long userId) {
        long now = clock.millis();
        Window window = windows.compute(userId, (ignored, existing) ->
                existing == null || now - existing.startedAt() >= windowMs
                        ? new Window(now, 1) : new Window(existing.startedAt(), existing.requests() + 1));
        if (window.requests() > limit) {
            throw new RateLimitExceededException("Too many shipping quote requests. Please try again later.");
        }
    }

    private record Window(long startedAt, int requests) {}
}
