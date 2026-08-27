package com.computerstore.user.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "account_action_tokens")
public class AccountActionToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AccountActionPurpose purpose;

    @Column(name = "target_email", length = 254)
    private String targetEmail;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AccountActionToken() {
    }

    public AccountActionToken(String tokenHash, UserAccount user, AccountActionPurpose purpose,
                              String targetEmail, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.user = user;
        this.purpose = purpose;
        this.targetEmail = targetEmail;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isUsable(Instant now, AccountActionPurpose expectedPurpose) {
        return purpose == expectedPurpose && consumedAt == null && expiresAt.isAfter(now);
    }

    public void consume(Instant now) {
        consumedAt = now;
    }

    public UserAccount getUser() { return user; }
    public String getTargetEmail() { return targetEmail; }
    public AccountActionPurpose getPurpose() { return purpose; }
}
