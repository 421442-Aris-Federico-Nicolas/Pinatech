package com.computerstore.guest.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.computerstore.auth.service.AuthRateLimiter;
import com.computerstore.email.TransactionalEmailService;
import com.computerstore.guest.domain.GuestCheckoutSession;
import com.computerstore.guest.dto.GuestEmailVerificationRequest;
import com.computerstore.guest.repository.GuestCheckoutSessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.*;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class GuestSessionServiceTest {
    @Test
    void verifiesTheExactChallengedEmailWithAShortLivedHashedCode() {
        Instant now = Instant.parse("2026-09-08T10:00:00Z");
        GuestCheckoutSessionRepository sessions = mock(GuestCheckoutSessionRepository.class);
        GuestRequestSecurity security = mock(GuestRequestSecurity.class);
        TransactionalEmailService email = mock(TransactionalEmailService.class);
        AuthRateLimiter rateLimiter = mock(AuthRateLimiter.class);
        GuestCheckoutEligibilityService eligibility = mock(GuestCheckoutEligibilityService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        GuestCheckoutSession session = new GuestCheckoutSession("a".repeat(64), "b".repeat(64), now,
                now.plus(Duration.ofHours(1)));
        when(security.session(request, true)).thenReturn(session);
        when(sessions.findByTokenHashForUpdate(session.getTokenHash())).thenReturn(Optional.of(session));
        when(request.getHeader("X-Guest-CSRF")).thenReturn("c".repeat(43));
        AtomicReference<String> code = new AtomicReference<>();
        doAnswer(invocation -> { code.set(invocation.getArgument(2)); return null; }).when(email)
                .sendGuestCheckoutCode(eq("ada@example.com"), eq("Ada"), anyString(), eq(Duration.ofMinutes(10)));
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
        GuestChallengeAttemptService challengeAttempts = new GuestChallengeAttemptService(
                sessions, encoder, Clock.fixed(now, ZoneOffset.UTC));
        GuestSessionService service = new GuestSessionService(sessions, security, email,
                encoder, challengeAttempts, rateLimiter, eligibility, Clock.fixed(now, ZoneOffset.UTC), Duration.ofHours(1),
                Duration.ofMinutes(10), Duration.ofMinutes(1), false, "Lax");

        service.requestVerification(new GuestEmailVerificationRequest.Start(" ADA@EXAMPLE.COM ", "Ada"), request);
        assertNotNull(session.getChallengeHash());
        assertNotEquals(code.get(), session.getChallengeHash());
        var response = service.confirm(new GuestEmailVerificationRequest.Confirm("ada@example.com", code.get()), request);

        assertTrue(response.emailVerified());
        assertEquals("ada@example.com", response.verifiedEmail());
        assertEquals("c".repeat(43), response.csrfToken());
        assertNull(session.getChallengeHash());
        InOrder limits = inOrder(rateLimiter);
        limits.verify(rateLimiter).checkAccountAction(null, "guest-email-ip", "all");
        limits.verify(rateLimiter).checkAccountAction(null, "guest-email-session", session.getId().toString());
        limits.verify(rateLimiter).checkAccountAction(null, "guest-email-address", "ada@example.com");
        limits.verify(rateLimiter).checkAccountAction(null, "guest-code-ip", "all");
        limits.verify(rateLimiter).checkAccountAction(null, "guest-code-session", session.getId().toString());
        limits.verify(rateLimiter).checkAccountAction(null, "guest-code-address", "ada@example.com");
        verify(eligibility, times(2)).requireUnregistered("ada@example.com");
    }

    @Test
    void commitsEveryFailedAttemptAndInvalidatesTheFifth() {
        Instant now = Instant.parse("2026-09-08T10:00:00Z");
        GuestCheckoutSessionRepository sessions = mock(GuestCheckoutSessionRepository.class);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
        GuestCheckoutSession session = new GuestCheckoutSession("a".repeat(64), "b".repeat(64), now,
                now.plus(Duration.ofHours(1)));
        session.startChallenge("ada@example.com", encoder.encode("123456"), now, now.plusSeconds(600));
        when(sessions.findByTokenHashForUpdate(session.getTokenHash())).thenReturn(Optional.of(session));
        GuestChallengeAttemptService attempts = new GuestChallengeAttemptService(
                sessions, encoder, Clock.fixed(now, ZoneOffset.UTC));

        for (int attempt = 1; attempt <= 5; attempt++) {
            assertFalse(attempts.verify(session.getTokenHash(), "ada@example.com", "000000").valid());
            assertEquals(attempt, session.getChallengeAttempts());
        }
        assertNull(session.getChallengeHash());
        assertFalse(attempts.verify(session.getTokenHash(), "ada@example.com", "123456").valid());
        verify(sessions, times(6)).findByTokenHashForUpdate(session.getTokenHash());
    }
}
