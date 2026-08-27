package com.computerstore.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ActionTokenRequest(@NotBlank @Size(max = 256) String token) {
}
