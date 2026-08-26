package com.computerstore.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank @Size(max = 256) String token,
        @NotBlank
        @Size(min = 8, max = 72)
        @Pattern(regexp = ".*[A-Z].*", message = "Password must contain an uppercase letter.")
        @Pattern(regexp = ".*[a-z].*", message = "Password must contain a lowercase letter.")
        @Pattern(regexp = ".*\\d.*", message = "Password must contain a digit.")
        String password
) {
}
