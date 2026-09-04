package com.computerstore.catalog.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class ProductShippingDataTest {

    @Test
    void reportsWhetherEveryRequiredShippingValueIsPresent() {
        Product complete = new Product("Mouse", "mouse", "Mouse gamer", BigDecimal.TEN,
                new Category("Peripherals", "peripherals"), new Brand("Pinatech"),
                500, 10, 20, 30, 1, false);
        Product legacy = new Product("Keyboard", "keyboard", "Mechanical keyboard", BigDecimal.TEN,
                new Category("Peripherals", "peripherals"), new Brand("Pinatech"),
                null, null, null, null, null, false);

        assertTrue(complete.hasCompleteShippingData());
        assertFalse(legacy.hasCompleteShippingData());
    }
}
