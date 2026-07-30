package com.computerstore.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import com.computerstore.auth.domain.RefreshToken;
import com.computerstore.auth.dto.LoginRequest;
import com.computerstore.auth.dto.RegisterRequest;
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
        assertNotNull(token.getValue().getFamilyId());
    }

    @Test
    void loginAndRegistrationCreateIndependentFamilies() {
        stubJwt();
        UserAccount loginUser = customer();
        UserAccount registeredUser = customer();
        when(users.findByEmailIgnoreCase("customer@example.com"))
                .thenReturn(Optional.of(loginUser), Optional.empty());
        when(passwordEncoder.matches("Password1", loginUser.getPasswordHash())).thenReturn(true);
        when(passwordEncoder.encode("Password1")).thenReturn("encoded");
        when(roles.findByName(RoleName.CUSTOMER)).thenReturn(Optional.of(new Role(RoleName.CUSTOMER)));
        when(users.save(any(UserAccount.class))).thenReturn(registeredUser);

        service.login(new LoginRequest("customer@example.com", "Password1"));
        service.register(new RegisterRequest(
                "Customer", "Example", "customer@example.com", "Password1", null));

        ArgumentCaptor<RefreshToken> tokens = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokens, times(2)).save(tokens.capture());
        assertNotEquals(tokens.getAllValues().get(0).getFamilyId(), tokens.getAllValues().get(1).getFamilyId());
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
        UUID familyId = UUID.randomUUID();
        RefreshToken storedToken = new RefreshToken(
                user, sha256(rawToken), familyId, Instant.now().plusSeconds(60));
        when(refreshTokens.findFamilyIdByTokenHash(eq(sha256(rawToken)))).thenReturn(Optional.of(familyId));
        when(refreshTokens.lockFamily(familyId)).thenReturn(Optional.of(storedToken));
        when(refreshTokens.findByTokenHash(eq(sha256(rawToken)))).thenReturn(Optional.of(storedToken));

        AuthSession session = service.refresh(rawToken);

        ArgumentCaptor<RefreshToken> successor = ArgumentCaptor.forClass(RefreshToken.class);
        assertNotEquals(rawToken, session.refreshToken());
        verify(refreshTokens).save(successor.capture());
        assertEquals(familyId, successor.getValue().getFamilyId());
        assertTrue(storedToken.isRevoked());
    }

    @Test
    void reuseOfRevokedTokenRevokesTheWholeFamily() throws Exception {
        String rawToken = "reused-refresh-token";
        UUID familyId = UUID.randomUUID();
        RefreshToken storedToken = new RefreshToken(
                customer(), sha256(rawToken), familyId, Instant.now().plusSeconds(60));
        storedToken.revoke();
        when(refreshTokens.findFamilyIdByTokenHash(eq(sha256(rawToken)))).thenReturn(Optional.of(familyId));
        when(refreshTokens.lockFamily(familyId)).thenReturn(Optional.of(storedToken));
        when(refreshTokens.findByTokenHash(eq(sha256(rawToken)))).thenReturn(Optional.of(storedToken));

        assertThrows(AuthenticationFailureException.class, () -> service.refresh(rawToken));

        verify(refreshTokens).revokeFamily(eq(familyId), any(Instant.class));
    }

    @Test
    void logoutLocksAndRevokesThePresentedTokensFamily() throws Exception {
        String rawToken = "presented-refresh-token";
        UUID familyId = UUID.randomUUID();
        RefreshToken storedToken = new RefreshToken(
                customer(), sha256(rawToken), familyId, Instant.now().plusSeconds(60));
        when(refreshTokens.findFamilyIdByTokenHash(eq(sha256(rawToken)))).thenReturn(Optional.of(familyId));
        when(refreshTokens.lockFamily(familyId)).thenReturn(Optional.of(storedToken));

        service.logout(rawToken);

        verify(refreshTokens).lockFamily(familyId);
        verify(refreshTokens).revokeFamily(eq(familyId), any(Instant.class));
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
