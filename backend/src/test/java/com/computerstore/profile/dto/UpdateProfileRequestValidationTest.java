package com.computerstore.profile.dto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class UpdateProfileRequestValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsArgentineDocumentFormatsAndBlankValues() {
        assertTrue(validator.validate(new UpdateProfileRequest(null, null, null, "20.123-456 78")).isEmpty());
        assertTrue(validator.validate(new UpdateProfileRequest(null, null, null, " - . ")).isEmpty());
    }

    @Test
    void rejectsInvalidCharactersAndDigitCounts() {
        assertFalse(validator.validate(new UpdateProfileRequest(null, null, null, "12A34567")).isEmpty());
        assertFalse(validator.validate(new UpdateProfileRequest(null, null, null, "123456")).isEmpty());
        assertFalse(validator.validate(new UpdateProfileRequest(null, null, null, "123456789012")).isEmpty());
    }
}
