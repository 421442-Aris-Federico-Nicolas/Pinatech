package com.computerstore.guest.service;

import com.computerstore.auth.service.AuthRateLimiter;
import com.computerstore.common.exception.GuestCheckoutAccountRequiredException;
import com.computerstore.guest.domain.GuestCheckoutSession;
import com.computerstore.user.repository.UserAccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GuestCheckoutEligibilityService {
    private final UserAccountRepository users;
    private final GuestRequestSecurity security;
    private final AuthRateLimiter rateLimiter;

    public GuestCheckoutEligibilityService(UserAccountRepository users, GuestRequestSecurity security,
            AuthRateLimiter rateLimiter) {
        this.users = users;
        this.security = security;
        this.rateLimiter = rateLimiter;
    }

    @Transactional(readOnly = true)
    public void check(String rawEmail, HttpServletRequest request) {
        security.validateOrigin(request);
        var session = security.session(request, true);
        String email = GuestCheckoutNormalizer.email(rawEmail);
        rateLimit(request, session, email, "guest-eligibility");
        requireUnregistered(email);
    }

    public void rateLimitOrderCheck(HttpServletRequest request, GuestCheckoutSession session,
            String normalizedEmail) {
        rateLimit(request, session, normalizedEmail, "guest-order-email");
    }

    public void requireUnregistered(String normalizedEmail) {
        if (users.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new GuestCheckoutAccountRequiredException();
        }
    }

    private void rateLimit(HttpServletRequest request, GuestCheckoutSession session,
            String normalizedEmail, String action) {
        rateLimiter.checkAccountAction(request.getRemoteAddr(), action + "-ip", "all");
        rateLimiter.checkAccountAction(request.getRemoteAddr(), action + "-session", session.getId().toString());
        rateLimiter.checkAccountAction(request.getRemoteAddr(), action + "-address", normalizedEmail);
    }
}
