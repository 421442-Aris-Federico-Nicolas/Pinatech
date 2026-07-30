package com.computerstore.service.dto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class CreateTicketRequestValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsBrandOnlyForNotebooks() {
        assertTrue(valid("Notebook", "Lenovo", "ThinkPad").isEmpty());
        assertTrue(valid("PlayStation", "", "5").isEmpty());
        assertTrue(valid("PC de escritorio", null, "").isEmpty());
    }

    @Test
    void rejectsMissingNotebookBrandAndBrandsForOtherDevices() {
        assertFalse(valid("Notebook", "", "ThinkPad").isEmpty());
        assertFalse(valid("PlayStation", "Sony", "5").isEmpty());
        assertFalse(valid("PC de escritorio", "Armada", "").isEmpty());
    }

    @Test
    void rejectsMissingModelsAndDesktopPcModels() {
        assertFalse(valid("Notebook", "Lenovo", "").isEmpty());
        assertFalse(valid("PlayStation", "", "").isEmpty());
        assertFalse(valid("PC de escritorio", "", "Armada").isEmpty());
    }

    @Test
    void rejectsUnsupportedDeviceTypes() {
        assertFalse(valid("Tablet", "", "Modelo").isEmpty());
    }

    private java.util.Set<jakarta.validation.ConstraintViolation<CreateTicketRequest>> valid(String deviceType, String brand, String model) {
        return validator.validate(new CreateTicketRequest(deviceType, brand, model, "No enciende"));
    }
}
