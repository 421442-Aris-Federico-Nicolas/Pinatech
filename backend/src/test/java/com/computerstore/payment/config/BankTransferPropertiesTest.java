package com.computerstore.payment.config;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class BankTransferPropertiesTest {
    @Test
    void disabledConfigurationIsAvailableOnlyWhenExplicitlyEnabledAndComplete() {
        var disabled = new BankTransferProperties(false, "", "", "", "", "", "ARS", null);
        assertFalse(disabled.available());

        var enabled = new BankTransferProperties(true, "Pinatech SA", "30-12345678-9", "Bank",
                "pinatech.pagos", "1234567890123456789012", "ars", Duration.ofHours(24));
        assertTrue(enabled.available());
        assertEquals("ARS", enabled.snapshot().getCurrency());
    }

    @Test
    void enabledConfigurationFailsFastWhenIncomplete() {
        assertThrows(IllegalStateException.class, () -> new BankTransferProperties(
                true, "Pinatech SA", "30-12345678-9", "Bank", "pinatech.pagos", "123", "ARS",
                Duration.ofHours(24)));
        assertThrows(IllegalStateException.class, () -> new BankTransferProperties(
                true, "Pinatech SA", "30-12345678-9", "Bank", "pinatech.pagos",
                "1234567890123456789012", "USD", Duration.ofHours(24)));
    }
}
