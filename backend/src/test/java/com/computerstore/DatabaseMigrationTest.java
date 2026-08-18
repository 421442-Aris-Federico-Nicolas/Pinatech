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
    void appliesAllMigrationsAndValidatesJpaMappings() {
        Integer columns = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'customer_orders'
                  AND column_name IN (
                      'reservation_expires_at', 'idempotency_key', 'request_hash', 'currency',
                      'payment_status', 'fulfillment_status', 'payment_method', 'delivery_method'
                  )
                """, Integer.class);

        assertEquals(8, columns);
        assertEquals("ARS", jdbc.queryForObject("""
                SELECT column_default
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'customer_orders'
                  AND column_name = 'currency'
                """, String.class).replace("'", "").replace("::character varying", ""));
        assertEquals("uuid:NO", jdbc.queryForObject("""
                SELECT data_type || ':' || is_nullable
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'refresh_tokens'
                  AND column_name = 'family_id'
                """, String.class));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND tablename = 'refresh_tokens'
                  AND indexname = 'idx_refresh_tokens_family_id'
                """, Integer.class));
        assertEquals(5, jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'product_images'
                  AND column_name IN ('storage_key', 'original_filename', 'content_type', 'size_bytes', 'created_at')
                """, Integer.class));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                   AND table_name = 'ticket_attachments'
                """, Integer.class));
        assertEquals(0, jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'technical_service_tickets'
                  AND column_name = 'serial_number'
                """, Integer.class));
        assertEquals(3, jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'product_specifications'
                  AND column_name IN ('group_name', 'is_highlighted', 'display_order')
                """, Integer.class));
        assertEquals(0, jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM products product
                LEFT JOIN product_variants variant ON variant.product_id = product.id
                LEFT JOIN inventory stock ON stock.variant_id = variant.id
                WHERE variant.id IS NULL OR stock.variant_id IS NULL
                """, Integer.class));
        assertEquals(10, jdbc.queryForObject("SELECT MIN(available_quantity) FROM inventory", Integer.class));
        assertEquals(2, jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('payment_attempts', 'payment_events')
                """, Integer.class));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND tablename = 'payment_events'
                  AND indexname = 'uq_payment_events_event_key'
                """, Integer.class));
        assertEquals(2, jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'payment_events'
                  AND column_name IN ('notification_payload_hash', 'provider_payload_hash')
                """, Integer.class));
        assertEquals("14", jdbc.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1",
                String.class));
    }
}
