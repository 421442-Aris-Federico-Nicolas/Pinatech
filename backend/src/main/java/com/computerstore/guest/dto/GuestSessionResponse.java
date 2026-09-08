package com.computerstore.guest.dto;

import java.time.Instant;

public record GuestSessionResponse(String csrfToken, Instant expiresAt, boolean emailVerified,
                                   String verifiedEmail) {}
