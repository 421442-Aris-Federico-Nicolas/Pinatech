package com.computerstore.auth.dto;

import java.util.Set;

public record AuthenticatedUserResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        String phone,
        Set<String> roles
) {
}
