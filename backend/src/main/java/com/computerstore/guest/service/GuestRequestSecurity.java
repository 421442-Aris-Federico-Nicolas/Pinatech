package com.computerstore.guest.service;

import com.computerstore.common.exception.ResourceNotFoundException;
import com.computerstore.guest.domain.GuestCheckoutSession;
import com.computerstore.guest.repository.GuestCheckoutSessionRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;

@Component
public class GuestRequestSecurity {
    private final GuestCheckoutSessionRepository sessions;
    private final Clock clock;
    private final CorsConfiguration origins = new CorsConfiguration();
    private final String cookieName;

    public GuestRequestSecurity(GuestCheckoutSessionRepository sessions, Clock clock,
            @Value("${app.cors.allowed-origin}") String allowedOrigin,
            @Value("${app.cors.allowed-origin-patterns:}") String allowedPatterns,
            @Value("${app.guest-checkout.cookie-name:guest_checkout}") String cookieName,
            Environment environment) {
        this.sessions = sessions;
        this.clock = clock;
        this.cookieName = cookieName;
        origins.setAllowedOrigins(java.util.List.of(allowedOrigin));
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            origins.setAllowedOriginPatterns(Arrays.stream(allowedPatterns.split(",")).map(String::trim)
                    .filter(value -> !value.isEmpty()).toList());
        }
    }

    public void validateOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null || origins.checkOrigin(origin) == null) notFound();
    }

    public GuestCheckoutSession session(HttpServletRequest request, boolean csrfRequired) {
        GuestCheckoutSession session = optionalSession(request, csrfRequired);
        return session == null ? notFound() : session;
    }

    public GuestCheckoutSession optionalSession(HttpServletRequest request, boolean csrfRequired) {
        String rawToken = cookie(request, cookieName);
        if (rawToken != null && rawToken.length() != 43) return null;
        GuestCheckoutSession session = rawToken == null ? null : sessions.findByTokenHash(sha256(rawToken)).orElse(null);
        if (session == null || session.isExpired(Instant.now(clock))) return null;
        if (csrfRequired) {
            String csrf = request.getHeader("X-Guest-CSRF");
            if (csrf == null || csrf.length() != 43 || !constantEquals(session.getCsrfHash(), sha256(csrf))) return null;
        }
        return session;
    }

    public String orderAccessHash(HttpServletRequest request) {
        String token = request.getHeader("X-Order-Access-Token");
        if (token == null || token.trim().length() != 43) return null;
        return sha256(token.trim());
    }

    public String cookieName() { return cookieName; }

    public static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    public static boolean constantEquals(String expected, String actual) {
        return expected != null && actual != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII), actual.getBytes(StandardCharsets.US_ASCII));
    }

    private String cookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies()).filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue).findFirst().orElse(null);
    }

    private <T> T notFound() { throw new ResourceNotFoundException("Guest checkout resource not found."); }
}
