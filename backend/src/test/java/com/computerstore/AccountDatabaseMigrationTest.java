package com.computerstore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AccountDatabaseMigrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void accountSchemaRequiresPasswordsAndCaseInsensitiveEmailUniqueness() {
        assertEquals(2, jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('user_addresses', 'account_action_tokens')
                """, Integer.class));
        assertEquals(0, jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('user_external_identities', 'google_auth_nonces')
                """, Integer.class));
        assertEquals("NO", jdbc.queryForObject("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'users' AND column_name = 'password_hash'
                """, String.class));
        assertEquals("0", jdbc.queryForObject("""
                SELECT column_default FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'users' AND column_name = 'session_version'
                """, String.class));
        assertEquals("0", jdbc.queryForObject("""
                SELECT column_default FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'refresh_tokens'
                  AND column_name = 'session_version'
                """, String.class));

        String email = "case-" + UUID.randomUUID() + "@example.com";
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO users (first_name, last_name, email, password_hash)
                VALUES ('Missing', 'Password', ?, NULL)
                """, "passwordless-" + email));
        Long userId = jdbc.queryForObject("""
                INSERT INTO users (first_name, last_name, email, password_hash)
                VALUES ('Local', 'User', ?, 'hash') RETURNING id
                """, Long.class, email);
        try {
            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                    INSERT INTO users (first_name, last_name, email, password_hash)
                    VALUES ('Duplicate', 'User', ?, 'hash')
                    """, email.toUpperCase()));
        } finally {
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
    }
}
