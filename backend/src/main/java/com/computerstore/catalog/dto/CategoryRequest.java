package com.computerstore.catalog.dto;
import jakarta.validation.constraints.*;
public record CategoryRequest(@NotBlank @Size(max=100) String name, @NotBlank @Size(max=120) String slug) {}
