package com.computerstore.profile.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(max = 100) @Pattern(regexp = ".*\\S.*") String firstName,
        @Size(max = 100) @Pattern(regexp = ".*\\S.*") String lastName,
        @Size(max = 30)
        @Pattern(regexp = "^$|^\\+?[0-9 ()-]{6,30}$", message = "Phone format is invalid.")
        String phone,
        @Pattern(
                regexp = "^[.\\s-]*$|^(?=(?:\\D*\\d){7,11}\\D*$)[0-9.\\s-]+$",
                message = "Document number format is invalid.")
        String documentNumber
) {
}
