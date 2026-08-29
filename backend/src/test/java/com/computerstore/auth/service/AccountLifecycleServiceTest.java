package com.computerstore.auth.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.computerstore.auth.repository.RefreshTokenRepository;
import com.computerstore.email.TransactionalEmailService;
import com.computerstore.user.domain.AccountActionPurpose;
import com.computerstore.user.domain.AccountActionToken;
import com.computerstore.user.domain.UserAccount;
import com.computerstore.user.repository.UserAccountRepository;
import com.computerstore.user.service.AccountActionTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AccountLifecycleServiceTest {

    @Mock private UserAccountRepository users;
    @Mock private RefreshTokenRepository refreshTokens;
    @Mock private AccountActionTokenService tokens;
    @Mock private TransactionalEmailService emails;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AccountActionToken actionToken;
    @Mock private UserAccount user;

    private AccountLifecycleService service;

    @BeforeEach
    void setUp() {
        service = new AccountLifecycleService(users, refreshTokens, tokens, emails, passwordEncoder);
    }

    @Test
    void resetPasswordConsumesTokenChangesPasswordAndRevokesEverySession() {
        when(tokens.consume("raw-token", AccountActionPurpose.PASSWORD_RESET)).thenReturn(actionToken);
        when(actionToken.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(9L);
        when(user.isActive()).thenReturn(true);
        when(users.findByIdForUpdate(9L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NewPassword1")).thenReturn("new-hash");

        service.resetPassword("raw-token", "NewPassword1");

        verify(user).setPasswordHash("new-hash");
        verify(user).incrementSessionVersion();
        verify(refreshTokens).revokeAllByUserId(any(), any());
        verify(tokens).invalidateAll(user);
    }

    @Test
    void passwordResetRequestDoesNotRevealAnUnknownAccount() {
        when(users.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());

        service.requestPasswordReset(" Missing@Example.com ");

        verify(tokens, never()).issue(any(), any(), any());
        verify(emails, never()).sendAccountAction(any(), any(), any(), any());
    }

    @Test
    void passwordResetRequestIssuesAHashedActionForAnActiveAccount() {
        when(user.getId()).thenReturn(9L);
        when(user.getEmail()).thenReturn("user@example.com");
        when(user.getFirstName()).thenReturn("Ana");
        when(user.isActive()).thenReturn(true);
        when(users.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(users.findByIdForUpdate(9L)).thenReturn(Optional.of(user));
        when(tokens.issue(user, AccountActionPurpose.PASSWORD_RESET, null)).thenReturn("raw-token");

        service.requestPasswordReset("user@example.com");

        verify(emails).sendAccountAction(
                "user@example.com", "Ana", AccountActionPurpose.PASSWORD_RESET, "raw-token");
    }

    @Test
    void emailVerificationUsesTheUsersFirstNameAndDedicatedEmailFlow() {
        when(user.isActive()).thenReturn(true);
        when(user.isEmailVerified()).thenReturn(false);
        when(user.getEmail()).thenReturn("user@example.com");
        when(user.getFirstName()).thenReturn("Ana");
        when(tokens.issue(user, AccountActionPurpose.EMAIL_VERIFICATION, null)).thenReturn("raw-token");

        service.startEmailVerification(user);

        verify(emails).sendEmailVerification("user@example.com", "Ana", "raw-token");
        verify(emails, never()).sendAccountAction(
                "user@example.com", "Ana", AccountActionPurpose.EMAIL_VERIFICATION, "raw-token");
    }

    @Test
    void emailVerificationMarksTheLockedAccountAsVerified() {
        when(tokens.consume("raw-token", AccountActionPurpose.EMAIL_VERIFICATION)).thenReturn(actionToken);
        when(actionToken.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(9L);
        when(user.isEmailVerified()).thenReturn(false);
        when(users.findByIdForUpdate(9L)).thenReturn(Optional.of(user));

        service.confirmEmailVerification("raw-token");

        verify(user).markEmailVerified();
    }
}
