package com.computerstore.order.dto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;

import com.computerstore.order.domain.FulfillmentMethod;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class CreateOrderRequestValidationTest {

    private static final String VERSION = "a".repeat(64);
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsAtMostFiftyValidItems() {
        var request = new CreateOrderRequest(Collections.nCopies(
                50, new CreateOrderRequest.Item(1L, 99)), FulfillmentMethod.PICKUP, "CORDOBA-CENTRO", VERSION);

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void rejectsTooManyItemsNonPositiveProductIdsAndExcessiveQuantities() {
        var tooManyItems = new CreateOrderRequest(Collections.nCopies(
                51, new CreateOrderRequest.Item(1L, 1)), FulfillmentMethod.PICKUP, "CORDOBA-CENTRO", VERSION);
        var invalidItem = new CreateOrderRequest(
                List.of(new CreateOrderRequest.Item(0L, 100)), FulfillmentMethod.PICKUP, "CORDOBA-CENTRO", VERSION);

        assertFalse(validator.validate(tooManyItems).isEmpty());
        assertFalse(validator.validate(invalidItem).isEmpty());
    }

    @Test
    void requiresFulfillmentMethodPickupLocationCodeAndVersion() {
        var missingMethod = new CreateOrderRequest(
                List.of(new CreateOrderRequest.Item(1L, 1)), null, "CODE", VERSION);
        var missingLocation = new CreateOrderRequest(
                List.of(new CreateOrderRequest.Item(1L, 1)), FulfillmentMethod.PICKUP, " ", VERSION);
        var missingVersion = new CreateOrderRequest(
                List.of(new CreateOrderRequest.Item(1L, 1)), FulfillmentMethod.PICKUP, "CODE", " ");

        assertFalse(validator.validate(missingMethod).isEmpty());
        assertFalse(validator.validate(missingLocation).isEmpty());
        assertFalse(validator.validate(missingVersion).isEmpty());
    }
}
