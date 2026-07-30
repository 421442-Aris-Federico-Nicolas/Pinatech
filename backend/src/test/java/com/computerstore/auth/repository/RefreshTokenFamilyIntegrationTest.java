package com.computerstore.auth.repository;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import com.computerstore.auth.domain.RefreshToken;
import com.computerstore.auth.service.AuthService;
import com.computerstore.common.exception.AuthenticationFailureException;
import com.computerstore.user.domain.UserAccount;
import com.computerstore.user.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class RefreshTokenFamilyIntegrationTest {

    @Autowired private AuthService authService;
    @Autowired private RefreshTokenRepository refreshTokens;
    @Autowired private UserAccountRepository users;

    @Test
    void reusedTokenRevocationCommitsDespiteAuthenticationFailure() throws Exception {
        UserAccount user = users.save(new UserAccount(
                "Refresh", "Test", "refresh-family@example.com", "hash", null));
        UUID familyId = UUID.randomUUID();
        String reusedRawToken = "already-used-refresh-token";
        String successorRawToken = "successor-refresh-token";
        RefreshToken reused = new RefreshToken(
                user, sha256(reusedRawToken), familyId, Instant.now().plusSeconds(60));
        reused.revoke();
        refreshTokens.save(reused);
        refreshTokens.save(new RefreshToken(
                user, sha256(successorRawToken), familyId, Instant.now().plusSeconds(60)));

        try {
            assertThrows(AuthenticationFailureException.class, () -> authService.refresh(reusedRawToken));

            assertTrue(refreshTokens.findByTokenHash(sha256(successorRawToken)).orElseThrow().isRevoked());
        } finally {
            refreshTokens.deleteAllInBatch();
            users.delete(user);
        }
    }

    private String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
