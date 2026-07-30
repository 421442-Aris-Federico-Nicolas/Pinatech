package com.computerstore.inventory.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record InventoryAdjustmentRequest(
        @NotNull @Positive Long variantId,
        @NotNull Integer quantity,
        @NotBlank @Size(max = 500) String reason
) {
    @AssertTrue(message = "quantity must not be zero")
    public boolean isQuantityNonZero() {
        return quantity == null || quantity != 0;
    }
}
