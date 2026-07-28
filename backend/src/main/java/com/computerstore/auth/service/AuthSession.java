package com.computerstore.auth.service;

import com.computerstore.auth.dto.AuthResponse;

public record AuthSession(AuthResponse response, String refreshToken) {
}
