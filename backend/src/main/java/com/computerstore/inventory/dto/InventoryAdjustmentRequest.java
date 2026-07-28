package com.computerstore.inventory.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
public record InventoryAdjustmentRequest(@NotNull Long productId, @NotNull Integer quantity, @NotBlank String reason) {}
