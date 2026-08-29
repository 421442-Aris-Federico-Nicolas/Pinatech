package com.computerstore.auth.service;

import java.time.Instant;

import com.computerstore.auth.repository.RefreshTokenRepository;
import com.computerstore.email.TransactionalEmailService;
import com.computerstore.user.domain.AccountActionPurpose;
import com.computerstore.user.domain.AccountActionToken;
import com.computerstore.user.domain.UserAccount;
import com.computerstore.user.repository.UserAccountRepository;
import com.computerstore.user.service.AccountActionTokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountLifecycleService {

    private final UserAccountRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccountActionTokenService tokenService;
    private final TransactionalEmailService emailService;
    private final PasswordEncoder passwordEncoder;

    public AccountLifecycleService(
            UserAccountRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            AccountActionTokenService tokenService,
            TransactionalEmailService emailService,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenService = tokenService;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void startEmailVerification(UserAccount user) {
        if (user.isActive() && !user.isEmailVerified()) {
            String token = tokenService.issue(user, AccountActionPurpose.EMAIL_VERIFICATION, null);
            emailService.sendEmailVerification(user.getEmail(), user.getFirstName(), token);
        }
    }

    @Transactional
    public void requestEmailVerification(String email) {
        userRepository.findByEmailIgnoreCase(normalizeEmail(email))
                .flatMap(user -> userRepository.findByIdForUpdate(user.getId()))
                .ifPresent(this::startEmailVerification);
    }

    @Transactional
    public void confirmEmailVerification(String rawToken) {
        AccountActionToken token = tokenService.consume(rawToken, AccountActionPurpose.EMAIL_VERIFICATION);
        UserAccount user = userRepository.findByIdForUpdate(token.getUser().getId())
                .orElseThrow(() -> new IllegalStateException("Account action user is missing."));
        if (!user.isEmailVerified()) {
            user.markEmailVerified();
        }
    }

    @Transactional
    public void requestPasswordReset(String email) {
        userRepository.findByEmailIgnoreCase(normalizeEmail(email))
                .filter(UserAccount::isActive)
                .flatMap(user -> userRepository.findByIdForUpdate(user.getId()))
                .ifPresent(user -> {
                    String token = tokenService.issue(user, AccountActionPurpose.PASSWORD_RESET, null);
                    emailService.sendAccountAction(
                            user.getEmail(), user.getFirstName(), AccountActionPurpose.PASSWORD_RESET, token);
                });
    }

    @Transactional
    public void resetPassword(String rawToken, String password) {
        AccountActionToken token = tokenService.consume(rawToken, AccountActionPurpose.PASSWORD_RESET);
        UserAccount user = userRepository.findByIdForUpdate(token.getUser().getId())
                .filter(UserAccount::isActive)
                .orElseThrow(() -> new IllegalStateException("Account action user is missing."));
        user.setPasswordHash(passwordEncoder.encode(password));
        user.incrementSessionVersion();
        refreshTokenRepository.revokeAllByUserId(user.getId(), Instant.now());
        tokenService.invalidateAll(user);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}
