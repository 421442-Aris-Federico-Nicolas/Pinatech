package com.computerstore.guest.service;

import com.computerstore.guest.domain.GuestCheckoutSession;
import com.computerstore.guest.repository.GuestCheckoutSessionRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GuestChallengeAttemptService {
    private final GuestCheckoutSessionRepository sessions;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public GuestChallengeAttemptService(GuestCheckoutSessionRepository sessions, PasswordEncoder passwordEncoder,
                                        Clock clock) {
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public VerificationResult verify(String sessionTokenHash, String email, String code) {
        GuestCheckoutSession session = sessions.findByTokenHashForUpdate(sessionTokenHash).orElse(null);
        Instant now = Instant.now(clock);
        boolean valid = session != null && !session.isExpired(now) && session.getChallengeHash() != null
                && session.getChallengeAttempts() < 5 && session.getChallengeExpiresAt().isAfter(now)
                && email.equals(session.getChallengeEmail())
                && passwordEncoder.matches(code, session.getChallengeHash());
        if (valid) {
            session.verifyEmail(now);
            return new VerificationResult(true, session);
        }
        if (session != null && session.getChallengeHash() != null) {
            session.challengeFailed();
            if (session.getChallengeAttempts() >= 5) session.invalidateChallenge();
        }
        return new VerificationResult(false, session);
    }

    public record VerificationResult(boolean valid, GuestCheckoutSession session) {}
}
