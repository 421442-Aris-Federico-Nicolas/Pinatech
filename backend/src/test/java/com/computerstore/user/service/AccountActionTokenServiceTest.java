package com.computerstore.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import com.computerstore.common.exception.InvalidRequestException;
import com.computerstore.user.domain.AccountActionPurpose;
import com.computerstore.user.domain.AccountActionToken;
import com.computerstore.user.domain.UserAccount;
import com.computerstore.user.repository.AccountActionTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountActionTokenServiceTest {

    @Mock private AccountActionTokenRepository tokens;
    @Mock private UserAccount user;

    private AccountActionTokenService service;

    @BeforeEach
    void setUp() {
        service = new AccountActionTokenService(
                tokens, Duration.ofHours(24), Duration.ofHours(1), Duration.ofHours(1));
    }

    @Test
    void issueInvalidatesPreviousTokensAndPersistsOnlyAHash() {
        when(user.getId()).thenReturn(7L);
        String rawToken = service.issue(user, AccountActionPurpose.EMAIL_VERIFICATION, null);

        ArgumentCaptor<AccountActionToken> persisted = ArgumentCaptor.forClass(AccountActionToken.class);
        verify(tokens).invalidateActive(any(), any(), any());
        verify(tokens).save(persisted.capture());
        assertEquals(32, Base64.getUrlDecoder().decode(rawToken).length);
        assertNotEquals(rawToken, AccountActionTokenService.hash(rawToken));
    }

    @Test
    void tokenCanOnlyBeConsumedOnce() {
        String rawToken = "valid-account-token";
        AccountActionToken token = new AccountActionToken(
                AccountActionTokenService.hash(rawToken), user, AccountActionPurpose.PASSWORD_RESET,
                null, Instant.now().plusSeconds(60));
        when(tokens.findByTokenHashForUpdate(AccountActionTokenService.hash(rawToken)))
                .thenReturn(Optional.of(token));

        service.consume(rawToken, AccountActionPurpose.PASSWORD_RESET);

        assertThrows(InvalidRequestException.class,
                () -> service.consume(rawToken, AccountActionPurpose.PASSWORD_RESET));
    }

    @Test
    void invalidatesEveryActivePurposeForAUser() {
        when(user.getId()).thenReturn(7L);
        when(tokens.invalidateAllActive(any(), any())).thenReturn(3);

        assertEquals(3, service.invalidateAll(user));

        verify(tokens).invalidateAllActive(any(), any());
    }

    @Test
    void rejectsNonPositiveTokenTtlsAtStartup() {
        assertThrows(IllegalArgumentException.class, () -> new AccountActionTokenService(
                tokens, Duration.ZERO, Duration.ofHours(1), Duration.ofHours(1)));
        assertThrows(IllegalArgumentException.class, () -> new AccountActionTokenService(
                tokens, Duration.ofHours(1), Duration.ofSeconds(-1), Duration.ofHours(1)));
    }
}
