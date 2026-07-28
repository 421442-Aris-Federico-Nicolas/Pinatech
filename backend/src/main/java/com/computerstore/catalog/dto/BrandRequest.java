package com.computerstore.catalog.dto;
import jakarta.validation.constraints.*;
public record BrandRequest(@NotBlank @Size(max=100) String name) {}
