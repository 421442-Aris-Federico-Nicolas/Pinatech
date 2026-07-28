package com.computerstore.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank
        @Size(min = 8, max = 72)
        @Pattern(regexp = ".*[A-Z].*", message = "Password must contain an uppercase letter.")
        @Pattern(regexp = ".*[a-z].*", message = "Password must contain a lowercase letter.")
        @Pattern(regexp = ".*\\d.*", message = "Password must contain a digit.")
        String password,
        @Size(max = 30) String phone
) {
}
