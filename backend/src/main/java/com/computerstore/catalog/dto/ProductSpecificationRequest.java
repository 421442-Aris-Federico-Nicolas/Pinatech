package com.computerstore.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductSpecificationRequest(
        @NotBlank @Size(max = 100) String groupName,
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 500) String value,
        boolean highlighted
) {}
