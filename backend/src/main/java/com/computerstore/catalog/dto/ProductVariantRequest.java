package com.computerstore.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ProductVariantRequest(
        @Positive Long id,
        @NotBlank @Size(max = 100) String colorName,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String colorHex
) {}
