package com.computerstore.catalog.dto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class ProductSpecificationRequestValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsValidGroupedSpecifications() {
        var request = product(List.of(new ProductSpecificationRequest("Sensor", "Resolución", "12000 dpi", true)));

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void rejectsIncompleteOrTooManySpecifications() {
        var incomplete = product(List.of(new ProductSpecificationRequest("", "Resolución", "", false)));
        var tooMany = product(Collections.nCopies(61,
                new ProductSpecificationRequest("General", "Dato", "Valor", false)));

        assertFalse(validator.validate(incomplete).isEmpty());
        assertFalse(validator.validate(tooMany).isEmpty());
    }

    private CreateProductRequest product(List<ProductSpecificationRequest> specifications) {
        return new CreateProductRequest("Mouse", "mouse", "Mouse gamer", BigDecimal.TEN, 1L, 1L,
                500, 10, 20, 30, 1, false, specifications,
                List.of(new ProductVariantRequest(null, "Black", "#000000", null)));
    }
}
