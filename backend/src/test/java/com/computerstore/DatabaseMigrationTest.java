package com.computerstore;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class DatabaseMigrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void appliesTheOrderReservationMigrationAndValidatesJpaMappings() {
        Integer columns = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'customer_orders'
                  AND column_name IN ('reservation_expires_at', 'idempotency_key', 'request_hash')
                """, Integer.class);

        assertEquals(3, columns);
        assertEquals("7", jdbc.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1",
                String.class));
    }
}
