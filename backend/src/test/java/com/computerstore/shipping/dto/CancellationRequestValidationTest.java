package com.computerstore.shipping.dto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.computerstore.order.domain.OrderCancellationReason;
import com.computerstore.order.dto.ConfirmBankTransferRefundRequest;
import com.computerstore.shipping.domain.ShipmentCancellationScope;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class CancellationRequestValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void cancellationRequiresScopeAndLimitsInternalDetail() {
        assertFalse(validator.validate(new CancelShipmentRequest(null, null, null)).isEmpty());
        assertFalse(validator.validate(new CancelShipmentRequest(ShipmentCancellationScope.ORDER,
                OrderCancellationReason.OTHER, "x".repeat(501))).isEmpty());
        assertTrue(validator.validate(new CancelShipmentRequest(ShipmentCancellationScope.ORDER,
                OrderCancellationReason.OTHER, "x".repeat(500))).isEmpty());
    }

    @Test
    void bankTransferReferenceIsOptionalAndLimited() {
        assertTrue(validator.validate(new ConfirmBankTransferRefundRequest(null)).isEmpty());
        assertFalse(validator.validate(new ConfirmBankTransferRefundRequest("x".repeat(201))).isEmpty());
    }
}
