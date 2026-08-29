package com.computerstore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
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
        assertEquals(9, jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'customer_orders'
                  AND column_name IN (
                      'fulfillment_method', 'pickup_location_code', 'pickup_location_name',
                      'pickup_address_lines', 'pickup_locality', 'pickup_province_code',
                      'pickup_postal_code', 'pickup_instructions', 'pickup_hours'
                  )
                """, Integer.class));
        assertEquals(8, jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'customer_orders'
                  AND column_name LIKE 'pickup_%'
                  AND is_nullable = 'YES'
                """, Integer.class));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_indexes
                WHERE schemaname = 'public'
                  AND tablename = 'customer_orders'
                  AND indexname = 'idx_customer_orders_fulfillment_method_location'
                """, Integer.class));
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
        assertEquals(3, jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('payment_attempts', 'payment_events', 'provider_payments')
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
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_indexes
                WHERE schemaname = 'public'
                  AND tablename = 'payment_attempts'
                  AND indexname = 'uq_payment_attempts_one_active_per_order'
                """, Integer.class));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_indexes
                WHERE schemaname = 'public'
                  AND tablename = 'provider_payments'
                  AND indexname = 'uq_provider_payments_provider_payment_id'
                """, Integer.class));
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
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_indexes
                WHERE schemaname = 'public' AND tablename = 'users'
                  AND indexname = 'uq_users_email_lower'
                """, Integer.class));
        assertEquals("NO", jdbc.queryForObject("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'users' AND column_name = 'password_hash'
                """, String.class));
        assertEquals(11, jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'customer_orders'
                  AND column_name IN ('payment_surcharge', 'payment_discount', 'payment_due_at', 'bank_holder', 'bank_tax_id',
                    'bank_name', 'bank_alias', 'bank_cbu', 'bank_currency', 'subtotal', 'total')
                """, Integer.class));
        assertEquals("0", jdbc.queryForObject("""
                SELECT column_default FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'customer_orders'
                  AND column_name = 'payment_discount'
                """, String.class));
        assertEquals(2, jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_constraint
                WHERE conrelid = 'customer_orders'::regclass
                  AND conname IN ('chk_customer_orders_payment_adjustments', 'chk_customer_orders_amounts')
                """, Integer.class));
        assertEquals(3, jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('bank_transfer_proofs', 'bank_transfer_proof_previews', 'email_outbox')
                """, Integer.class));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'public'
                  AND tablename = 'customer_orders'
                  AND indexname = 'uq_customer_orders_one_pending_transfer_per_user'
                """, Integer.class));
        assertEquals("22", jdbc.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1",
                String.class));
    }

    @Test
    void migratesLegacyPlayStationTicketsToConsola() throws IOException {
        Long userId = jdbc.queryForObject("""
                INSERT INTO users (first_name, last_name, email, password_hash)
                VALUES ('Migration', 'Test', ?, 'hash') RETURNING id
                """, Long.class, "ticket-migration-" + UUID.randomUUID() + "@example.com");
        Long ticketId = jdbc.queryForObject("""
                INSERT INTO technical_service_tickets (
                    customer_id, device_type, brand, model, reported_problem, status
                ) VALUES (?, 'PlayStation', 'Legacy brand', 'Legacy model', 'No enciende', 'RECEIVED')
                RETURNING id
                """, Long.class, userId);
        try {
            String migration = new ClassPathResource(
                    "db/migration/V20__rename_playstation_device_type_to_consola.sql")
                    .getContentAsString(StandardCharsets.UTF_8);
            jdbc.execute(migration);

            assertEquals("Consola", jdbc.queryForObject(
                    "SELECT device_type FROM technical_service_tickets WHERE id = ?",
                    String.class, ticketId));
        } finally {
            jdbc.update("DELETE FROM technical_service_tickets WHERE id = ?", ticketId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
    }

    @Test
    void databaseSerializesActivePreferencesAndProviderPaymentIds() {
        Long userId = jdbc.queryForObject("""
                INSERT INTO users (first_name, last_name, email, password_hash)
                VALUES ('Constraint', 'Test', ?, 'hash') RETURNING id
                """, Long.class, "constraint-" + UUID.randomUUID() + "@example.com");
        Long orderId = jdbc.queryForObject("""
                INSERT INTO customer_orders (
                    user_id, status, subtotal, total, reservation_expires_at,
                    currency, payment_status, fulfillment_status, payment_method
                ) VALUES (?, 'PENDING_PAYMENT', 100, 100, CURRENT_TIMESTAMP + INTERVAL '10 minutes',
                          'ARS', 'PENDING', 'PENDING', 'MERCADO_PAGO') RETURNING id
                """, Long.class, userId);
        try {
            Long attemptId = insertAttempt(orderId, "constraint-a");
            assertThrows(DataIntegrityViolationException.class,
                    () -> insertAttempt(orderId, "constraint-b"));

            jdbc.update("""
                    INSERT INTO provider_payments (
                        attempt_id, provider_payment_id, provider_status, amount, currency,
                        live_mode, operation_type, amount_refunded
                    ) VALUES (?, 'provider-constraint-id', 'approved', 100, 'ARS', FALSE,
                              'regular_payment', 0)
                    """, attemptId);
            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                    INSERT INTO provider_payments (
                        attempt_id, provider_payment_id, provider_status, amount, currency,
                        live_mode, operation_type, amount_refunded
                    ) VALUES (?, 'provider-constraint-id', 'approved', 100, 'ARS', FALSE,
                              'regular_payment', 0)
                    """, attemptId));
        } finally {
            jdbc.update("DELETE FROM provider_payments WHERE attempt_id IN (SELECT id FROM payment_attempts WHERE order_id = ?)", orderId);
            jdbc.update("DELETE FROM payment_attempts WHERE order_id = ?", orderId);
            jdbc.update("DELETE FROM customer_orders WHERE id = ?", orderId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
    }

    @Test
    void defaultsPaymentMethodForOrdersInsertedByThePreviousBackendDuringDeployment() {
        Long userId = jdbc.queryForObject("""
                INSERT INTO users (first_name, last_name, email, password_hash)
                VALUES ('Rolling', 'Deploy', ?, 'hash') RETURNING id
                """, Long.class, "rolling-deploy-" + UUID.randomUUID() + "@example.com");
        Long orderId = null;
        try {
            orderId = jdbc.queryForObject("""
                    INSERT INTO customer_orders (
                        user_id, status, subtotal, total, reservation_expires_at,
                        currency, payment_status, fulfillment_status, payment_method
                    ) VALUES (?, 'PENDING_PAYMENT', 100, 100, CURRENT_TIMESTAMP + INTERVAL '10 minutes',
                              'ARS', 'PENDING', 'PENDING', NULL) RETURNING id
                    """, Long.class, userId);

            assertEquals("MERCADO_PAGO", jdbc.queryForObject(
                    "SELECT payment_method FROM customer_orders WHERE id = ?", String.class, orderId));
        } finally {
            if (orderId != null) jdbc.update("DELETE FROM customer_orders WHERE id = ?", orderId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
    }

    private Long insertAttempt(Long orderId, String key) {
        return jdbc.queryForObject("""
                INSERT INTO payment_attempts (
                    public_id, order_id, provider, status, idempotency_key,
                    amount, currency, expires_at
                ) VALUES (?, ?, 'MERCADO_PAGO', 'CREATED', ?, 100, 'ARS',
                          CURRENT_TIMESTAMP + INTERVAL '10 minutes') RETURNING id
                """, Long.class, UUID.randomUUID(), orderId, key);
    }
}
