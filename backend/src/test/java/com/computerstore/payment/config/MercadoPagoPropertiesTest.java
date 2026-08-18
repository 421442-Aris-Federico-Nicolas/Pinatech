package com.computerstore.payment.config;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.Test;

class MercadoPagoPropertiesTest {

    @Test
    void enabledIntegrationRequiresAllCredentialsAndPublicUrl() {
        assertThrows(IllegalArgumentException.class, () -> new MercadoPagoProperties(
                true,
                MercadoPagoEnvironment.SANDBOX,
                "",
                "",
                "",
                URI.create(""),
                Duration.ofSeconds(1),
                Duration.ofSeconds(2),
                false,
                Duration.ofMinutes(5),
                Duration.ofDays(30)));
    }

    @Test
    void enabledIntegrationRejectsRelativePublicUrl() {
        assertThrows(IllegalArgumentException.class, () -> new MercadoPagoProperties(
                true,
                MercadoPagoEnvironment.PRODUCTION,
                "APP_USR-token",
                "secret",
                "99",
                URI.create("checkout"),
                Duration.ofSeconds(1),
                Duration.ofSeconds(2),
                true,
                Duration.ofMinutes(5),
                Duration.ofDays(30)));
    }

    @Test
    void productionRequiresExplicitConfirmationAndProductionCredentials() {
        assertThrows(IllegalArgumentException.class, () -> new MercadoPagoProperties(
                true, MercadoPagoEnvironment.PRODUCTION, "TEST-token", "secret", "99",
                URI.create("https://store.example"), Duration.ofSeconds(1), Duration.ofSeconds(2),
                false, Duration.ofMinutes(5), Duration.ofDays(30)));
    }
}
