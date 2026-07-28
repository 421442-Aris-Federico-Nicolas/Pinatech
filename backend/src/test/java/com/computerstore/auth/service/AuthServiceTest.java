package com.computerstore.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import com.computerstore.auth.domain.RefreshToken;
import com.computerstore.auth.dto.LoginRequest;
import com.computerstore.auth.repository.RefreshTokenRepository;
import com.computerstore.common.exception.AuthenticationFailureException;
import com.computerstore.security.JwtService;
import com.computerstore.user.domain.Role;
import com.computerstore.user.domain.RoleName;
import com.computerstore.user.domain.UserAccount;
import com.computerstore.user.repository.RoleRepository;
import com.computerstore.user.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserAccountRepository users;
    @Mock private RoleRepository roles;
    @Mock private RefreshTokenRepository refreshTokens;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(users, roles, refreshTokens, passwordEncoder, jwtService, 60_000);
    }

    private void stubJwt() {
        when(jwtService.generateAccessToken(any())).thenReturn("access-token");
        when(jwtService.getAccessExpirationSeconds()).thenReturn(900L);
    }

    @Test
    void loginCreatesHashedRefreshTokenForValidCredentials() throws Exception {
        stubJwt();
        UserAccount user = customer();
        when(users.findByEmailIgnoreCase("customer@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", user.getPasswordHash())).thenReturn(true);

        AuthSession session = service.login(new LoginRequest(" Customer@Example.com ", "password"));

        ArgumentCaptor<RefreshToken> token = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokens).save(token.capture());
        assertEquals("access-token", session.response().accessToken());
        assertNotEquals(session.refreshToken(), token.getValue().getTokenHash());
        assertEquals(sha256(session.refreshToken()), token.getValue().getTokenHash());
    }

    @Test
    void loginRejectsInvalidCredentialsWithoutCreatingSession() {
        UserAccount user = customer();
        when(users.findByEmailIgnoreCase("customer@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", user.getPasswordHash())).thenReturn(false);

        assertThrows(AuthenticationFailureException.class,
                () -> service.login(new LoginRequest("customer@example.com", "wrong")));
        verify(refreshTokens, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void refreshRevokesThePresentedTokenAndCreatesANewSession() throws Exception {
        stubJwt();
        UserAccount user = customer();
        String rawToken = "presented-refresh-token";
        RefreshToken storedToken = new RefreshToken(user, sha256(rawToken), Instant.now().plusSeconds(60));
        when(refreshTokens.findByTokenHash(eq(sha256(rawToken)))).thenReturn(Optional.of(storedToken));

        AuthSession session = service.refresh(rawToken);

        assertNotEquals(rawToken, session.refreshToken());
        assertThrows(AuthenticationFailureException.class, () -> service.refresh(rawToken));
        verify(refreshTokens).save(any(RefreshToken.class));
    }

    private UserAccount customer() {
        UserAccount user = new UserAccount("Customer", "Example", "customer@example.com", "hash", null);
        user.addRole(new Role(RoleName.CUSTOMER));
        return user;
    }

    private String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
