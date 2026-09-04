package com.computerstore.shipping.config;

import java.time.Duration;

import com.computerstore.common.exception.BusinessRuleException;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.shipping.zipnova")
public record ZipnovaProperties(
        boolean enabled,
        boolean productionConfirmation,
        String token,
        String secret,
        Long accountId,
        Long originId,
        String source,
        String packagingMode,
        Duration quoteTtl,
        Duration connectTimeout,
        Duration readTimeout,
        String webhookSecret,
        Duration reconciliationInterval
) {
    public ZipnovaProperties {
        source = blank(source) ? "pinatech" : source.trim();
        packagingMode = blank(packagingMode) ? "dynamic" : packagingMode.trim();
        quoteTtl = quoteTtl == null ? Duration.ofMinutes(15) : quoteTtl;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(3) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(15) : readTimeout;
        reconciliationInterval = reconciliationInterval == null ? Duration.ofMinutes(10) : reconciliationInterval;
        if (!java.util.Set.of("dynamic", "boxes", "none").contains(packagingMode)) {
            throw new IllegalArgumentException("Zipnova packaging mode must be dynamic, boxes or none.");
        }
        if (quoteTtl.isNegative() || quoteTtl.isZero() || connectTimeout.isNegative() || connectTimeout.isZero()
                || readTimeout.isNegative() || readTimeout.isZero()
                || reconciliationInterval.isNegative() || reconciliationInterval.isZero()) {
            throw new IllegalArgumentException("Zipnova durations must be positive.");
        }
        if (source.length() > 150) throw new IllegalArgumentException("Zipnova source is too long.");
        if (enabled && (!productionConfirmation || blank(token) || blank(secret) || blank(webhookSecret)
                || accountId == null || accountId <= 0 || originId == null || originId <= 0)) {
            throw new IllegalArgumentException("Enabled Zipnova requires credentials, account/origin IDs, webhook secret and production confirmation.");
        }
        if (enabled && !webhookSecret.trim().matches("[A-Za-z0-9_-]{24,200}")) {
            throw new IllegalArgumentException("Zipnova webhook secret must be a URL-safe value between 24 and 200 characters.");
        }
    }

    public boolean available() { return enabled && productionConfirmation; }

    public void requireAvailable() {
        if (!available()) throw new BusinessRuleException("Delivery shipping is currently unavailable.");
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
