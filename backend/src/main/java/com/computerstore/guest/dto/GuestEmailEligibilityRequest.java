package com.computerstore.guest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GuestEmailEligibilityRequest(
        @NotBlank @Size(max = 256) String email
) {}
