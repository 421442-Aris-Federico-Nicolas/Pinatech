package com.computerstore.order.dto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class CreateOrderRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsAtMostFiftyValidItems() {
        var request = new CreateOrderRequest(Collections.nCopies(
                50, new CreateOrderRequest.Item(1L, 99)));

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void rejectsTooManyItemsNonPositiveProductIdsAndExcessiveQuantities() {
        var tooManyItems = new CreateOrderRequest(Collections.nCopies(
                51, new CreateOrderRequest.Item(1L, 1)));
        var invalidItem = new CreateOrderRequest(List.of(new CreateOrderRequest.Item(0L, 100)));

        assertFalse(validator.validate(tooManyItems).isEmpty());
        assertFalse(validator.validate(invalidItem).isEmpty());
    }
}
