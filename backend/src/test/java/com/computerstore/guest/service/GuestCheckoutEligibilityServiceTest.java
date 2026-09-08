package com.computerstore.guest.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.computerstore.auth.service.AuthRateLimiter;
import com.computerstore.common.exception.GuestCheckoutAccountRequiredException;
import com.computerstore.guest.domain.GuestCheckoutSession;
import com.computerstore.user.repository.UserAccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class GuestCheckoutEligibilityServiceTest {

    @Test
    void rateLimitsAndRejectsANormalizedRegisteredEmail() {
        UserAccountRepository users = mock(UserAccountRepository.class);
        GuestRequestSecurity security = mock(GuestRequestSecurity.class);
        AuthRateLimiter rateLimiter = mock(AuthRateLimiter.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        GuestCheckoutSession session = new GuestCheckoutSession("a".repeat(64), "b".repeat(64), Instant.now(),
                Instant.now().plus(Duration.ofHours(1)));
        when(request.getRemoteAddr()).thenReturn("203.0.113.5");
        when(security.session(request, true)).thenReturn(session);
        when(users.existsByEmailIgnoreCase("ada@example.com")).thenReturn(true);
        var service = new GuestCheckoutEligibilityService(users, security, rateLimiter);

        assertThrows(GuestCheckoutAccountRequiredException.class,
                () -> service.check(" ADA@EXAMPLE.COM ", request));

        var ordered = inOrder(security, rateLimiter, users);
        ordered.verify(security).validateOrigin(request);
        ordered.verify(security).session(request, true);
        ordered.verify(rateLimiter).checkAccountAction("203.0.113.5", "guest-eligibility-ip", "all");
        ordered.verify(rateLimiter).checkAccountAction("203.0.113.5", "guest-eligibility-session",
                session.getId().toString());
        ordered.verify(rateLimiter).checkAccountAction("203.0.113.5", "guest-eligibility-address",
                "ada@example.com");
        ordered.verify(users).existsByEmailIgnoreCase("ada@example.com");
    }

    @Test
    void acceptsAnEmailWithoutAnAccount() {
        UserAccountRepository users = mock(UserAccountRepository.class);
        var service = new GuestCheckoutEligibilityService(users, mock(GuestRequestSecurity.class),
                mock(AuthRateLimiter.class));

        assertDoesNotThrow(() -> service.requireUnregistered("new@example.com"));
    }

    @Test
    void rateLimitsFinalOrderChecksByIpSessionAndEmail() {
        AuthRateLimiter rateLimiter = mock(AuthRateLimiter.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        GuestCheckoutSession session = new GuestCheckoutSession("a".repeat(64), "b".repeat(64), Instant.now(),
                Instant.now().plus(Duration.ofHours(1)));
        when(request.getRemoteAddr()).thenReturn("203.0.113.5");
        var service = new GuestCheckoutEligibilityService(mock(UserAccountRepository.class),
                mock(GuestRequestSecurity.class), rateLimiter);

        service.rateLimitOrderCheck(request, session, "ada@example.com");

        verify(rateLimiter).checkAccountAction("203.0.113.5", "guest-order-email-ip", "all");
        verify(rateLimiter).checkAccountAction("203.0.113.5", "guest-order-email-session",
                session.getId().toString());
        verify(rateLimiter).checkAccountAction("203.0.113.5", "guest-order-email-address", "ada@example.com");
    }
}
