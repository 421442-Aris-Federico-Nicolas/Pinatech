package com.computerstore.guest.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "guest_checkout_sessions")
public class GuestCheckoutSession {
    @Id private UUID id;
    @Column(name = "token_hash", nullable = false, updatable = false, length = 64) private String tokenHash;
    @Column(name = "csrf_hash", nullable = false, length = 64) private String csrfHash;
    @Column(name = "verified_email", length = 254) private String verifiedEmail;
    @Column(name = "email_verified_at") private Instant emailVerifiedAt;
    @Column(name = "challenge_email", length = 254) private String challengeEmail;
    @Column(name = "challenge_hash", length = 255) private String challengeHash;
    @Column(name = "challenge_expires_at") private Instant challengeExpiresAt;
    @Column(name = "challenge_attempts", nullable = false) private int challengeAttempts;
    @Column(name = "challenge_requested_at") private Instant challengeRequestedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;

    protected GuestCheckoutSession() {}

    public GuestCheckoutSession(String tokenHash, String csrfHash, Instant now, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.tokenHash = tokenHash;
        this.csrfHash = csrfHash;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    public void startChallenge(String email, String hash, Instant now, Instant expiresAt) {
        this.challengeEmail = email;
        this.challengeHash = hash;
        this.challengeExpiresAt = expiresAt;
        this.challengeRequestedAt = now;
        this.challengeAttempts = 0;
    }

    public void challengeFailed() { challengeAttempts++; }

    public void invalidateChallenge() {
        challengeEmail = null;
        challengeHash = null;
        challengeExpiresAt = null;
        challengeAttempts = 5;
    }

    public void rotateCsrf(String hash) { csrfHash = hash; }

    public void verifyEmail(Instant now) {
        verifiedEmail = challengeEmail;
        emailVerifiedAt = now;
        challengeEmail = null;
        challengeHash = null;
        challengeExpiresAt = null;
        challengeAttempts = 0;
    }

    public UUID getId() { return id; }
    public String getTokenHash() { return tokenHash; }
    public String getCsrfHash() { return csrfHash; }
    public String getVerifiedEmail() { return verifiedEmail; }
    public Instant getEmailVerifiedAt() { return emailVerifiedAt; }
    public String getChallengeEmail() { return challengeEmail; }
    public String getChallengeHash() { return challengeHash; }
    public Instant getChallengeExpiresAt() { return challengeExpiresAt; }
    public int getChallengeAttempts() { return challengeAttempts; }
    public Instant getChallengeRequestedAt() { return challengeRequestedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isExpired(Instant now) { return !expiresAt.isAfter(now); }
    public boolean isEmailVerified(String email) { return verifiedEmail != null && verifiedEmail.equals(email); }
}
