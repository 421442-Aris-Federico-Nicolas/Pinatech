package com.computerstore.catalog.dto;
import java.math.BigDecimal;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
public record CreateProductRequest(
        @NotBlank @Size(max=150) String name,
        @NotBlank @Size(max=180) String slug,
        @NotBlank @Size(max=2000) String description,
        @NotNull @DecimalMin("0.01") @Digits(integer=17, fraction=2) BigDecimal price,
        @NotNull Long categoryId,
        @NotNull Long brandId,
        @NotNull @Min(10) @Max(10000000) Integer shippingWeightGrams,
        @NotNull @Min(1) @Max(5000) Integer shippingHeightCm,
        @NotNull @Min(1) @Max(5000) Integer shippingWidthCm,
        @NotNull @Min(1) @Max(5000) Integer shippingLengthCm,
        @NotNull @Min(1) @Max(8) Integer shippingClassificationId,
        boolean mustKeepVertical,
        @NotNull @Size(max=60) List<@Valid ProductSpecificationRequest> specifications,
        @NotNull @Size(min=1,max=20) List<@Valid ProductVariantRequest> variants
) {
    public CreateProductRequest {
        if (shippingClassificationId == null) {
            shippingClassificationId = 1;
        }
    }
}
