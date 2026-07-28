package com.computerstore.auth.dto;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        AuthenticatedUserResponse user
) {
}
