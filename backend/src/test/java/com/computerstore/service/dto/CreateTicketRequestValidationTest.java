package com.computerstore.service.dto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class CreateTicketRequestValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsValidConsoleNotebookAndDesktopPcDetails() {
        assertTrue(valid("Consola", "Sony", "Modelo 5").isEmpty());
        assertTrue(valid("Notebook", "Lenovo", "ThinkPad").isEmpty());
        assertTrue(valid("PC de escritorio", null, "").isEmpty());
    }

    @Test
    void rejectsMissingRequiredBrandsAndDesktopPcBrands() {
        assertFalse(valid("Consola", "", "Modelo 5").isEmpty());
        assertFalse(valid("Notebook", "", "ThinkPad").isEmpty());
        assertFalse(valid("PC de escritorio", "Armada", "").isEmpty());
    }

    @Test
    void rejectsMissingModelsAndDesktopPcModels() {
        assertFalse(valid("Consola", "Sony", "").isEmpty());
        assertFalse(valid("Notebook", "Lenovo", "").isEmpty());
        assertFalse(valid("PC de escritorio", "", "Armada").isEmpty());
    }

    @Test
    void rejectsUnsupportedDeviceTypes() {
        assertFalse(valid("Tablet", "", "Modelo").isEmpty());
        assertFalse(valid("PlayStation", "Sony", "Modelo 5").isEmpty());
    }

    private java.util.Set<jakarta.validation.ConstraintViolation<CreateTicketRequest>> valid(String deviceType, String brand, String model) {
        return validator.validate(new CreateTicketRequest(deviceType, brand, model, "No enciende"));
    }
}
