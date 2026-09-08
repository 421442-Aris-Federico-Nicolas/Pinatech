package com.computerstore.home.dto;

import com.computerstore.home.domain.HomeSectionMode;
import com.computerstore.home.domain.HomeSectionSort;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record HomeSectionRequest(
        @Size(max = 100) String eyebrow,
        @NotBlank @Size(max = 150) String title,
        @Size(max = 1000) String description,
        @Size(max = 100) String buttonLabel,
        @NotNull HomeSectionMode mode,
        @Min(1) @Max(12) int productLimit,
        HomeSectionSort sort,
        @NotNull @Size(max = 20) List<@NotNull Long> categoryIds,
        @NotNull List<@NotNull Long> productIds,
        boolean active) {}
