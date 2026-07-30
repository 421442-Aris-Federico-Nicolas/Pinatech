package com.computerstore.catalog.dto;
import java.math.BigDecimal;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
public record UpdateProductRequest(@NotBlank @Size(max=150) String name, @NotBlank @Size(max=180) String slug, @NotBlank @Size(max=2000) String description, @NotNull @DecimalMin("0.01") @Digits(integer=17, fraction=2) BigDecimal price, @NotNull Long categoryId, @NotNull Long brandId, @NotNull @Size(max=60) List<@Valid ProductSpecificationRequest> specifications, @NotNull @Size(min=1,max=20) List<@Valid ProductVariantRequest> variants) {}
