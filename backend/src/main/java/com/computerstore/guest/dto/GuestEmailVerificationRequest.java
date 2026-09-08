package com.computerstore.guest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class GuestEmailVerificationRequest {
    private GuestEmailVerificationRequest() {}

    public record Start(@NotBlank @Size(max = 256) String email,
                        @NotBlank @Size(max = 100) String firstName) {}
    public record Confirm(@NotBlank @Size(max = 256) String email,
                          @NotBlank @Pattern(regexp = "^[0-9]{6}$") String code) {}
    public record Accepted(String message) {}
}
