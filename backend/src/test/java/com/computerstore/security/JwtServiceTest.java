package com.computerstore.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class JwtServiceTest {

    @Test
    void accessTokenCarriesTheSessionVersion() {
        JwtService service = new JwtService("01234567890123456789012345678901", 60_000);
        AuthenticatedUser user = new AuthenticatedUser(42L, "user@example.com", 6L, List.of());

        JwtService.JwtSession session = service.extractSession(service.generateAccessToken(user));

        assertEquals(42L, session.userId());
        assertEquals(6L, session.sessionVersion());
    }
}
