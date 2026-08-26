package com.computerstore.order.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import com.computerstore.order.config.FulfillmentProperties;
import com.computerstore.order.domain.FulfillmentMethod;
import com.computerstore.common.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

class FulfillmentPolicyTest {

    @Test
    void incompleteOrDisabledPickupConfigurationIsNotAvailable() {
        FulfillmentPolicy incomplete = new FulfillmentPolicy(new FulfillmentProperties(
                new FulfillmentProperties.Pickup(
                        true, "CORDOBA-CENTRO", "Pinatech", List.of("Street 123"),
                        "Cordoba", "X", "5000", "", "Monday to Friday")));
        FulfillmentPolicy disabled = new FulfillmentPolicy(new FulfillmentProperties(
                new FulfillmentProperties.Pickup(
                        false, "CORDOBA-CENTRO", "Pinatech", List.of("Street 123"),
                        "Cordoba", "X", "5000", "Bring your ID.", "Monday to Friday")));

        assertTrue(incomplete.availableMethods().isEmpty());
        assertTrue(incomplete.activePickupLocation().isEmpty());
        assertTrue(disabled.availableMethods().isEmpty());
    }

    @Test
    void completeConfigurationPublishesPickup() {
        FulfillmentPolicy policy = new FulfillmentPolicy(new FulfillmentProperties(
                new FulfillmentProperties.Pickup(
                        true, " CORDOBA-CENTRO ", " Pinatech ", List.of(" Street 123 "),
                        " Cordoba ", " X ", " 5000 ", " Bring your ID. ", " Monday to Friday ")));

        assertEquals(List.of(FulfillmentMethod.PICKUP), policy.availableMethods());
        assertEquals("Street 123", policy.activePickupLocation().orElseThrow().getAddressLines().getFirst());
    }

    @Test
    void selectionRequiresTheCurrentVersionOfEveryPublicPickupField() {
        FulfillmentPolicy policy = new FulfillmentPolicy(new FulfillmentProperties(
                new FulfillmentProperties.Pickup(
                        true, "CORDOBA-CENTRO", "Pinatech", List.of("Street 123"),
                        "Cordoba", "X", "5000", "Bring your ID.", "Monday to Friday")));
        var location = policy.activePickupLocation().orElseThrow();

        assertEquals(location.version(), policy.select(
                FulfillmentMethod.PICKUP, location.getCode(), location.version()).version());
        assertThrows(InvalidRequestException.class,
                () -> policy.select(FulfillmentMethod.PICKUP, location.getCode(), "stale-version"));
    }
}
