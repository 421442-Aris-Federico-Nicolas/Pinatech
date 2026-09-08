package com.computerstore.guest.service;

import com.computerstore.auth.service.AuthRateLimiter;
import com.computerstore.common.exception.InvalidRequestException;
import com.computerstore.common.exception.RateLimitExceededException;
import com.computerstore.email.TransactionalEmailService;
import com.computerstore.guest.domain.GuestCheckoutSession;
import com.computerstore.guest.dto.GuestEmailVerificationRequest;
import com.computerstore.guest.dto.GuestSessionResponse;
import com.computerstore.guest.repository.GuestCheckoutSessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.security.SecureRandom;
import java.time.*;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GuestSessionService {
    private final GuestCheckoutSessionRepository sessions;
    private final GuestRequestSecurity security;
    private final TransactionalEmailService email;
    private final PasswordEncoder passwordEncoder;
    private final GuestChallengeAttemptService challengeAttempts;
    private final AuthRateLimiter rateLimiter;
    private final GuestCheckoutEligibilityService eligibility;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Duration sessionTtl;
    private final Duration challengeTtl;
    private final Duration challengeCooldown;
    private final boolean cookieSecure;
    private final String sameSite;

    public GuestSessionService(GuestCheckoutSessionRepository sessions, GuestRequestSecurity security,
            TransactionalEmailService email, PasswordEncoder passwordEncoder,
            GuestChallengeAttemptService challengeAttempts, AuthRateLimiter rateLimiter,
            GuestCheckoutEligibilityService eligibility, Clock clock,
            @Value("${app.guest-checkout.session-ttl:PT24H}") Duration sessionTtl,
            @Value("${app.guest-checkout.challenge-ttl:PT10M}") Duration challengeTtl,
            @Value("${app.guest-checkout.challenge-cooldown:PT1M}") Duration challengeCooldown,
            @Value("${app.guest-checkout.cookie-secure:${app.cookies.secure:false}}") boolean cookieSecure,
            @Value("${app.guest-checkout.cookie-same-site:Lax}") String sameSite) {
        this.sessions = sessions; this.security = security; this.email = email;
        this.passwordEncoder = passwordEncoder; this.challengeAttempts = challengeAttempts;
        this.rateLimiter = rateLimiter; this.eligibility = eligibility; this.clock = clock;
        this.sessionTtl = sessionTtl; this.challengeTtl = challengeTtl; this.challengeCooldown = challengeCooldown;
        this.cookieSecure = cookieSecure; this.sameSite = sameSite;
        if (sessionTtl.isNegative() || sessionTtl.isZero() || challengeTtl.isNegative() || challengeTtl.isZero()
                || challengeCooldown.isNegative() || challengeCooldown.isZero()) {
            throw new IllegalStateException("Guest checkout TTLs must be positive.");
        }
        if (!java.util.Set.of("Strict", "Lax", "None").contains(sameSite)
                || ("None".equals(sameSite) && !cookieSecure)) {
            throw new IllegalStateException("Guest checkout SameSite must be Strict, Lax or secure None.");
        }
    }

    @Transactional
    public CreatedSession create(HttpServletRequest request) {
        security.validateOrigin(request);
        rateLimiter.checkAccountAction(request.getRemoteAddr(), "guest-session", "all");
        Instant now = Instant.now(clock);
        String token = randomToken();
        String csrf = randomToken();
        GuestCheckoutSession session = sessions.save(new GuestCheckoutSession(
                GuestRequestSecurity.sha256(token), GuestRequestSecurity.sha256(csrf), now, now.plus(sessionTtl)));
        return new CreatedSession(response(session, csrf), cookie(token, sessionTtl));
    }

    @Transactional
    public GuestSessionResponse refreshCsrf(HttpServletRequest request) {
        GuestCheckoutSession session = security.session(request, false);
        String csrf = randomToken();
        session.rotateCsrf(GuestRequestSecurity.sha256(csrf));
        return response(session, csrf);
    }

    @Transactional
    public GuestEmailVerificationRequest.Accepted requestVerification(GuestEmailVerificationRequest.Start request,
                                                                       HttpServletRequest servletRequest) {
        security.validateOrigin(servletRequest);
        GuestCheckoutSession current = security.session(servletRequest, true);
        GuestCheckoutSession session = sessions.findByTokenHashForUpdate(current.getTokenHash()).orElseThrow();
        String normalizedEmail = GuestCheckoutNormalizer.email(request.email());
        rateLimiter.checkAccountAction(servletRequest.getRemoteAddr(), "guest-email-ip", "all");
        rateLimiter.checkAccountAction(servletRequest.getRemoteAddr(), "guest-email-session", session.getId().toString());
        rateLimiter.checkAccountAction(servletRequest.getRemoteAddr(), "guest-email-address", normalizedEmail);
        eligibility.requireUnregistered(normalizedEmail);
        Instant now = Instant.now(clock);
        if (session.getChallengeRequestedAt() != null
                && session.getChallengeRequestedAt().plus(challengeCooldown).isAfter(now)) {
            throw new RateLimitExceededException("Please wait before requesting another verification code.");
        }
        String code = "%06d".formatted(random.nextInt(1_000_000));
        session.startChallenge(normalizedEmail, passwordEncoder.encode(code), now, now.plus(challengeTtl));
        email.sendGuestCheckoutCode(normalizedEmail, request.firstName().trim(), code, challengeTtl);
        return new GuestEmailVerificationRequest.Accepted(
                "If the address can receive email, a verification code was sent.");
    }

    public GuestSessionResponse confirm(GuestEmailVerificationRequest.Confirm request,
                                        HttpServletRequest servletRequest) {
        security.validateOrigin(servletRequest);
        GuestCheckoutSession current = security.session(servletRequest, true);
        String normalizedEmail = GuestCheckoutNormalizer.email(request.email());
        rateLimiter.checkAccountAction(servletRequest.getRemoteAddr(), "guest-code-ip", "all");
        rateLimiter.checkAccountAction(servletRequest.getRemoteAddr(), "guest-code-session", current.getId().toString());
        rateLimiter.checkAccountAction(servletRequest.getRemoteAddr(), "guest-code-address", normalizedEmail);
        eligibility.requireUnregistered(normalizedEmail);
        var result = challengeAttempts.verify(current.getTokenHash(), normalizedEmail, request.code());
        if (!result.valid()) {
            throw new InvalidRequestException("The verification code is invalid or expired.");
        }
        return response(result.session(), servletRequest.getHeader("X-Guest-CSRF"));
    }

    private GuestSessionResponse response(GuestCheckoutSession session, String csrf) {
        return new GuestSessionResponse(csrf, session.getExpiresAt(), session.getEmailVerifiedAt() != null,
                session.getVerifiedEmail());
    }

    private ResponseCookie cookie(String token, Duration ttl) {
        return ResponseCookie.from(security.cookieName(), token).httpOnly(true).secure(cookieSecure)
                .sameSite(sameSite).path("/api").maxAge(ttl).build();
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record CreatedSession(GuestSessionResponse response, ResponseCookie cookie) {}
}
