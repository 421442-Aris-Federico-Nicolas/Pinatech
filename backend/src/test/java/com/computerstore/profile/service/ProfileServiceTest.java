package com.computerstore.profile.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;

import java.util.Optional;

import com.computerstore.auth.repository.RefreshTokenRepository;
import com.computerstore.common.exception.AuthenticationFailureException;
import com.computerstore.email.TransactionalEmailService;
import com.computerstore.common.exception.InvalidRequestException;
import com.computerstore.profile.dto.UpdateProfileRequest;
import com.computerstore.user.domain.AccountActionPurpose;
import com.computerstore.user.domain.AccountActionToken;
import com.computerstore.user.domain.UserAccount;
import com.computerstore.user.repository.UserAccountRepository;
import com.computerstore.user.repository.UserAddressRepository;
import com.computerstore.user.service.AccountActionTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock private UserAccountRepository users;
    @Mock private UserAddressRepository addresses;
    @Mock private RefreshTokenRepository refreshTokens;
    @Mock private AccountActionTokenService tokens;
    @Mock private TransactionalEmailService emails;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AccountActionToken actionToken;
    @Mock private UserAccount tokenUser;
    @Mock private UserAccount user;

    private ProfileService service;

    @BeforeEach
    void setUp() {
        service = new ProfileService(
                users, addresses, refreshTokens, tokens, emails, passwordEncoder);
    }

    @Test
    void emailChangesOnlyOnConfirmationAndInvalidatesEverySession() {
        when(tokens.consume("raw-token", AccountActionPurpose.EMAIL_CHANGE)).thenReturn(actionToken);
        when(actionToken.getUser()).thenReturn(tokenUser);
        when(tokenUser.getId()).thenReturn(4L);
        when(actionToken.getTargetEmail()).thenReturn("new@example.com");
        when(users.findByIdForUpdate(4L)).thenReturn(Optional.of(user));
        when(user.isActive()).thenReturn(true);
        when(user.getId()).thenReturn(4L);
        when(user.getEmail()).thenReturn("old@example.com");
        when(user.getFirstName()).thenReturn("Ana");
        when(users.findByEmailIgnoreCase("new@example.com")).thenReturn(Optional.empty());

        service.confirmEmailChange("raw-token");

        verify(user).changeEmail("new@example.com");
        verify(user).markEmailVerified();
        verify(user).incrementSessionVersion();
        verify(refreshTokens).revokeAllByUserId(any(), any());
        verify(tokens).invalidateAll(user);
        verify(emails).sendEmailChangedNotice("old@example.com", "Ana", "new@example.com");
    }

    @Test
    void emailChangeRequiresTheCurrentLocalPassword() {
        when(users.findByIdForUpdate(4L)).thenReturn(Optional.of(user));
        when(user.isActive()).thenReturn(true);
        when(user.getPasswordHash()).thenReturn("hash");
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThrows(AuthenticationFailureException.class,
                () -> service.requestEmailChange(4L, "new@example.com", "wrong"));

        verify(tokens, never()).issue(any(), any(), any());
    }

    @Test
    void emailChangeConfirmationUsesTheCustomersFirstName() {
        when(users.findByIdForUpdate(4L)).thenReturn(Optional.of(user));
        when(user.isActive()).thenReturn(true);
        when(user.getEmail()).thenReturn("old@example.com");
        when(user.getPasswordHash()).thenReturn("hash");
        when(user.getFirstName()).thenReturn("Ana");
        when(passwordEncoder.matches("Password1", "hash")).thenReturn(true);
        when(users.existsByEmailIgnoreCase("new@example.com")).thenReturn(false);
        when(tokens.issue(user, AccountActionPurpose.EMAIL_CHANGE, "new@example.com")).thenReturn("raw-token");

        service.requestEmailChange(4L, "New@Example.com", "Password1");

        verify(emails).sendAccountAction(
                "new@example.com", "Ana", AccountActionPurpose.EMAIL_CHANGE, "raw-token");
    }

    @Test
    void normalizesAndClearsTheProfileDocumentNumber() {
        UserAccount account = new UserAccount("Ana", "Perez", "ana@example.com", "hash", null);
        ReflectionTestUtils.setField(account, "id", 4L);
        when(users.findByIdAndActiveTrue(4L)).thenReturn(Optional.of(account));
        when(addresses.findById(4L)).thenReturn(Optional.empty());

        var updated = service.updateProfile(4L,
                new UpdateProfileRequest(null, null, null, "20.123-456 78"));

        assertEquals("2012345678", account.getDocumentNumber());
        assertEquals("2012345678", updated.documentNumber());

        var cleared = service.updateProfile(4L, new UpdateProfileRequest(null, null, null, " - . "));

        assertNull(account.getDocumentNumber());
        assertNull(cleared.documentNumber());
    }

    @Test
    void rejectsAnInvalidDocumentNumberWhenCalledWithoutControllerValidation() {
        UserAccount account = new UserAccount("Ana", "Perez", "ana@example.com", "hash", null);
        ReflectionTestUtils.setField(account, "id", 4L);
        when(users.findByIdAndActiveTrue(4L)).thenReturn(Optional.of(account));

        assertThrows(InvalidRequestException.class,
                () -> service.updateProfile(4L, new UpdateProfileRequest(null, null, null, "12A34567")));
    }
}
