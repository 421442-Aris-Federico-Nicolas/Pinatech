package com.computerstore.catalog.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class ProductShippingDataValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void createDefaultsClassificationAndAcceptsValidShippingData() {
        CreateProductRequest request = create(10, 1, 5000, 25, null);

        assertEquals(1, request.shippingClassificationId());
        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void rejectsShippingValuesOutsideTheirRanges() {
        assertFalse(validator.validate(create(9, 1, 1, 1, 1)).isEmpty());
        assertFalse(validator.validate(create(10, 5001, 1, 1, 1)).isEmpty());
        assertFalse(validator.validate(create(10, 1, 1, 1, 9)).isEmpty());

        UpdateProductRequest missingWeight = new UpdateProductRequest("Mouse", "mouse", "Mouse gamer",
                BigDecimal.TEN, 1L, 1L, null, 10, 20, 30, 1, false, List.of(), variants());
        assertFalse(validator.validate(missingWeight).isEmpty());
    }

    private CreateProductRequest create(Integer weight, Integer height, Integer width, Integer length,
            Integer classificationId) {
        return new CreateProductRequest("Mouse", "mouse", "Mouse gamer", BigDecimal.TEN, 1L, 1L,
                weight, height, width, length, classificationId, false, List.of(), variants());
    }

    private List<ProductVariantRequest> variants() {
        return List.of(new ProductVariantRequest(null, "Black", "#000000", null));
    }
}
