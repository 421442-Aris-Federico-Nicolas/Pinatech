package com.computerstore.home.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HomeHeroSlideRequest(
        @NotBlank @Size(max = 100) String eyebrow,
        @NotBlank @Size(max = 150) String title,
        @NotBlank @Size(max = 150) String accent,
        @NotBlank @Size(max = 500) String description,
        @NotBlank @Size(max = 200) String link,
        @NotBlank @Size(max = 100) String linkLabel,
        boolean showLoginLink,
        @NotBlank @Size(max = 200) String altText,
        @jakarta.validation.constraints.NotNull Boolean active) {}