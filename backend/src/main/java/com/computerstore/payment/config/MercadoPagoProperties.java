package com.computerstore.payment.config;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import com.computerstore.common.exception.BusinessRuleException;

@ConfigurationProperties("app.payments.mercado-pago")
public record MercadoPagoProperties(
        boolean enabled,
        MercadoPagoEnvironment environment,
        String accessToken,
        String webhookSecret,
        String collectorId,
        URI publicBaseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        boolean productionConfirmation,
        Duration webhookTimestampTolerance,
        Duration reconciliationLookback
) {
    public MercadoPagoProperties {
        environment = environment == null ? MercadoPagoEnvironment.SANDBOX : environment;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(3) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(10) : readTimeout;
        webhookTimestampTolerance = webhookTimestampTolerance == null
                ? Duration.ofMinutes(5) : webhookTimestampTolerance;
        reconciliationLookback = reconciliationLookback == null
                ? Duration.ofDays(30) : reconciliationLookback;
        if (connectTimeout.isZero() || connectTimeout.isNegative()
                || readTimeout.isZero() || readTimeout.isNegative()) {
            throw new IllegalArgumentException("Mercado Pago timeouts must be positive.");
        }
        if (webhookTimestampTolerance.isZero() || webhookTimestampTolerance.isNegative()) {
            throw new IllegalArgumentException("Mercado Pago webhook timestamp tolerance must be positive.");
        }
        if (reconciliationLookback.isZero() || reconciliationLookback.isNegative()) {
            throw new IllegalArgumentException("Mercado Pago reconciliation lookback must be positive.");
        }
        if (enabled && (blank(accessToken) || blank(webhookSecret) || blank(collectorId) || publicBaseUrl == null)) {
            throw new IllegalArgumentException(
                    "Enabled Mercado Pago integration requires access token, webhook secret, collector ID and public base URL.");
        }
        if (enabled && !publicBaseUrl.isAbsolute()) {
            throw new IllegalArgumentException("Mercado Pago public base URL must be absolute.");
        }
        if (enabled && !"https".equalsIgnoreCase(publicBaseUrl.getScheme())) {
            throw new IllegalArgumentException("Enabled Mercado Pago integration requires an HTTPS public base URL.");
        }
        if (enabled && environment == MercadoPagoEnvironment.SANDBOX && !accessToken.startsWith("TEST-")) {
            throw new IllegalArgumentException("Mercado Pago sandbox requires a TEST- access token.");
        }
        if (enabled && environment == MercadoPagoEnvironment.PRODUCTION
                && (!accessToken.startsWith("APP_USR-") || !productionConfirmation)) {
            throw new IllegalArgumentException(
                    "Mercado Pago production requires APP_USR- credentials and MP_PRODUCTION_CONFIRMATION=true.");
        }
    }

    public void requireEnabled() {
        if (!enabled) {
            throw new BusinessRuleException("Mercado Pago payments are disabled.");
        }
    }

    public String publicUrl(String path) {
        String base = publicBaseUrl.toString().replaceAll("/+$", "");
        return base + (path.startsWith("/") ? path : "/" + path);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
