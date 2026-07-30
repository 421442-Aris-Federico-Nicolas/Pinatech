package com.computerstore.catalog.dto;
import java.math.BigDecimal;
import jakarta.validation.constraints.*;
public record UpdateProductRequest(@NotBlank @Size(max=150) String name, @NotBlank @Size(max=180) String slug, @NotBlank @Size(max=2000) String description, @NotNull @DecimalMin("0.01") @Digits(integer=17, fraction=2) BigDecimal price, @NotNull Long categoryId, @NotNull Long brandId) {}
