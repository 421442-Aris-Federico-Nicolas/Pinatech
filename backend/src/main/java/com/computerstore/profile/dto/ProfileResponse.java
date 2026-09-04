package com.computerstore.profile.dto;

import java.util.Set;

public record ProfileResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        String phone,
        String documentNumber,
        boolean emailVerified,
        Set<String> roles,
        AddressResponse address
) {
}
