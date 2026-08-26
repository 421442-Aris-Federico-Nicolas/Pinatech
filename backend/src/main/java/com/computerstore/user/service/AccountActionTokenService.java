package com.computerstore.user.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import com.computerstore.common.exception.InvalidRequestException;
import com.computerstore.user.domain.AccountActionPurpose;
import com.computerstore.user.domain.AccountActionToken;
import com.computerstore.user.domain.UserAccount;
import com.computerstore.user.repository.AccountActionTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountActionTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AccountActionTokenRepository tokenRepository;
    private final Duration verificationTtl;
    private final Duration passwordResetTtl;
    private final Duration emailChangeTtl;

    public AccountActionTokenService(
            AccountActionTokenRepository tokenRepository,
            @Value("${app.account-tokens.email-verification-ttl}") Duration verificationTtl,
            @Value("${app.account-tokens.password-reset-ttl}") Duration passwordResetTtl,
            @Value("${app.account-tokens.email-change-ttl}") Duration emailChangeTtl
    ) {
        if (!positive(verificationTtl) || !positive(passwordResetTtl) || !positive(emailChangeTtl)) {
            throw new IllegalArgumentException("Account action token TTLs must be positive.");
        }
        this.tokenRepository = tokenRepository;
        this.verificationTtl = verificationTtl;
        this.passwordResetTtl = passwordResetTtl;
        this.emailChangeTtl = emailChangeTtl;
    }

    @Transactional
    public String issue(UserAccount user, AccountActionPurpose purpose, String targetEmail) {
        Instant now = Instant.now();
        tokenRepository.invalidateActive(user.getId(), purpose, now);
        String rawToken = generateToken();
        tokenRepository.save(new AccountActionToken(
                hash(rawToken), user, purpose, targetEmail, now.plus(ttlFor(purpose))));
        return rawToken;
    }

    @Transactional
    public AccountActionToken consume(String rawToken, AccountActionPurpose purpose) {
        AccountActionToken token = tokenRepository.findByTokenHashForUpdate(hash(rawToken))
                .orElseThrow(this::invalidToken);
        Instant now = Instant.now();
        if (!token.isUsable(now, purpose)) {
            throw invalidToken();
        }
        token.consume(now);
        return token;
    }

    @Transactional
    public int invalidateAll(UserAccount user) {
        return tokenRepository.invalidateAllActive(user.getId(), Instant.now());
    }

    private Duration ttlFor(AccountActionPurpose purpose) {
        return switch (purpose) {
            case EMAIL_VERIFICATION -> verificationTtl;
            case PASSWORD_RESET -> passwordResetTtl;
            case EMAIL_CHANGE -> emailChangeTtl;
        };
    }

    private boolean positive(Duration value) {
        return value != null && !value.isZero() && !value.isNegative();
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private InvalidRequestException invalidToken() {
        return new InvalidRequestException("The account action token is invalid or expired.");
    }
}
