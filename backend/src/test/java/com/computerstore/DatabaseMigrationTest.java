package com.computerstore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessException;
import com.computerstore.guest.domain.GuestCheckoutSession;
import com.computerstore.guest.repository.GuestCheckoutSessionRepository;
import com.computerstore.guest.service.GuestChallengeAttemptService;
import com.computerstore.user.service.AccountEmailLockService;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.Instant;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class DatabaseMigrationTest {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired private GuestCheckoutSessionRepository guestSessions;
    @Autowired private GuestChallengeAttemptService guestChallengeAttempts;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AccountEmailLockService accountEmailLock;
    @Autowired private PlatformTransactionManager transactionManager;

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
        assertEquals("YES", jdbc.queryForObject("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'product_variants'
                  AND column_name = 'image_id'
                """, String.class));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_constraint
                WHERE conrelid = 'product_variants'::regclass
                  AND conname = 'fk_product_variants_image_product'
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
        assertEquals("YES", jdbc.queryForObject("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'email_outbox'
                  AND column_name = 'seller_payload'
                """, String.class));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_constraint
                WHERE conrelid = 'email_outbox'::regclass
                  AND conname = 'chk_email_outbox_seller_payload'
                """, Integer.class));
        assertEquals("YES", jdbc.queryForObject("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'shipment_events'
                  AND column_name = 'provider_shipment_id'
                """, String.class));
        assertEquals("NO", jdbc.queryForObject("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'email_outbox'
                  AND column_name = 'deduplication_key'
                """, String.class));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_constraint
                WHERE conrelid = 'email_outbox'::regclass
                  AND conname = 'uq_email_outbox_order_event_key'
                """, Integer.class));
        assertTrue(jdbc.queryForObject("""
                SELECT pg_get_constraintdef(oid) FROM pg_constraint
                WHERE conrelid = 'provider_payments'::regclass
                  AND conname = 'chk_provider_payments_refund_status'
                """, String.class).contains("AMOUNT_MISMATCH"));
        assertEquals(4, jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('home_sections', 'home_section_categories',
                    'home_section_products', 'home_section_banners')
                """, Integer.class));
        assertEquals(2, jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_constraint
                WHERE conrelid = 'home_section_products'::regclass
                  AND conname IN ('pk_home_section_products', 'uq_home_section_products_order')
                  AND condeferrable AND condeferred
                """, Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM home_sections", Integer.class));
        assertEquals(List.of("Completá tu setup", "Potencia para tu equipo"), jdbc.queryForList(
                "SELECT title FROM home_sections ORDER BY display_order", String.class));
        assertEquals("/pinatech-banner-perifericos.webp", jdbc.queryForObject("""
                SELECT banner.external_url FROM home_section_banners banner
                JOIN home_sections section ON section.id = banner.section_id
                WHERE section.title = 'Completá tu setup' AND banner.device = 'DESKTOP'
                """, String.class));
        assertEquals(0, jdbc.queryForObject("""
                SELECT COUNT(*) FROM home_section_categories selected
                JOIN home_sections section ON section.id = selected.section_id
                JOIN categories category ON category.id = selected.category_id
                WHERE section.title = 'Potencia para tu equipo' AND category.slug = 'perifericos'
                """, Integer.class));
        assertEquals(3, jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_constraint
                WHERE conname IN ('chk_product_images_storage_metadata',
                                  'chk_home_section_banners_source',
                                  'home_hero_images_content_type_check')
                  AND pg_get_constraintdef(oid) LIKE '%image/webp%'
                """, Integer.class));
        assertEquals("34", jdbc.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1",
                String.class));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = 'guest_checkout_sessions'
                """, Integer.class));
        assertEquals("YES", jdbc.queryForObject("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'customer_orders' AND column_name = 'user_id'
                """, String.class));
        assertEquals(2, jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'public' AND tablename = 'customer_orders'
                  AND indexname IN ('uq_customer_orders_guest_idempotency_key',
                                    'uq_customer_orders_one_pending_per_guest')
                """, Integer.class));
        assertEquals(2, jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'email_outbox'
                  AND column_name IN ('order_public_id', 'guest_order') AND is_nullable = 'NO'
                """, Integer.class));
    }

    @Test
    void variantImagesMustBelongToTheProductAndAreClearedWhenDeleted() {
        List<Long> productIds = jdbc.queryForList("SELECT id FROM products ORDER BY id LIMIT 2", Long.class);
        Long productId = productIds.get(0);
        Long otherProductId = productIds.get(1);
        Long variantId = jdbc.queryForObject(
                "SELECT id FROM product_variants WHERE product_id = ? ORDER BY id LIMIT 1",
                Long.class, productId);
        Long otherVariantId = jdbc.queryForObject(
                "SELECT id FROM product_variants WHERE product_id = ? ORDER BY id LIMIT 1",
                Long.class, otherProductId);
        Long imageId = jdbc.queryForObject("""
                INSERT INTO product_images (product_id, image_url, alt_text, display_order)
                VALUES (?, ?, 'Variant migration test',
                    (SELECT COALESCE(MAX(display_order), -1) + 1 FROM product_images WHERE product_id = ?))
                RETURNING id
                """, Long.class, productId, "/migration-test-" + UUID.randomUUID(), productId);
        try {
            assertThrows(DataIntegrityViolationException.class,
                    () -> jdbc.update("UPDATE product_variants SET image_id = ? WHERE id = ?", imageId, otherVariantId));

            jdbc.update("UPDATE product_variants SET image_id = ? WHERE id = ?", imageId, variantId);
            jdbc.update("DELETE FROM product_images WHERE id = ?", imageId);

            assertNull(jdbc.queryForObject("SELECT image_id FROM product_variants WHERE id = ?", Long.class, variantId));
            assertEquals(productId,
                    jdbc.queryForObject("SELECT product_id FROM product_variants WHERE id = ?", Long.class, variantId));
        } finally {
            jdbc.update("UPDATE product_variants SET image_id = NULL WHERE image_id = ?", imageId);
            jdbc.update("DELETE FROM product_images WHERE id = ?", imageId);
        }
    }

    @Test
    void homeSectionChecksRejectIncompleteAutomaticSortAndStoredBannerMetadata() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO home_sections (display_order, title, mode, product_limit, sort)
                VALUES ((SELECT MAX(display_order) + 1 FROM home_sections), 'Invalid automatic',
                        'AUTOMATIC', 12, NULL)
                """));

        Long sectionId = jdbc.queryForObject("SELECT id FROM home_sections ORDER BY id LIMIT 1", Long.class);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO home_section_banners (section_id, device, storage_key)
                VALUES (?, 'MOBILE', ?)
                """, sectionId, UUID.randomUUID().toString()));
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

    @Test
    void snapshotsRegisteredBuyersAndPreventsIdentityChanges() {
        String email = "snapshot-" + UUID.randomUUID() + "@example.com";
        Long userId = jdbc.queryForObject("""
                INSERT INTO users (first_name, last_name, email, password_hash, phone, document_number)
                VALUES ('Snapshot', 'Buyer', ?, 'hash', '3515550101', '12345678') RETURNING id
                """, Long.class, email);
        Long orderId = null;
        try {
            orderId = jdbc.queryForObject("""
                    INSERT INTO customer_orders (
                        user_id, status, subtotal, total, reservation_expires_at,
                        currency, payment_status, fulfillment_status, payment_method
                    ) VALUES (?, 'PENDING_PAYMENT', 100, 100, CURRENT_TIMESTAMP + INTERVAL '10 minutes',
                              'ARS', 'PENDING', 'PENDING', 'MERCADO_PAGO') RETURNING id
                    """, Long.class, userId);
            assertEquals(email, jdbc.queryForObject(
                    "SELECT buyer_email FROM customer_orders WHERE id = ?", String.class, orderId));
            assertEquals("12345678", jdbc.queryForObject(
                    "SELECT buyer_document_number FROM customer_orders WHERE id = ?", String.class, orderId));
            Long immutableOrderId = orderId;
            assertThrows(DataAccessException.class, () -> jdbc.update(
                    "UPDATE customer_orders SET buyer_email = 'changed@example.com' WHERE id = ?", immutableOrderId));
        } finally {
            if (orderId != null) jdbc.update("DELETE FROM customer_orders WHERE id = ?", orderId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
    }

    @Test
    void persistsAndLocksGuestChallengeAfterFiveFailures() {
        Instant now = Instant.now();
        GuestCheckoutSession session = new GuestCheckoutSession(
                UUID.randomUUID().toString().replace("-", "").repeat(2), "b".repeat(64), now, now.plusSeconds(3600));
        session.startChallenge("challenge@example.com", passwordEncoder.encode("123456"), now, now.plusSeconds(600));
        guestSessions.save(session);
        try {
            for (int attempt = 1; attempt <= 5; attempt++) {
                assertTrue(!guestChallengeAttempts.verify(
                        session.getTokenHash(), "challenge@example.com", "000000").valid());
                GuestCheckoutSession persisted = guestSessions.findById(session.getId()).orElseThrow();
                assertEquals(attempt, persisted.getChallengeAttempts());
            }
            assertNull(guestSessions.findById(session.getId()).orElseThrow().getChallengeHash());
        } finally {
            guestSessions.deleteById(session.getId());
        }
    }

    @Test
    void allowsOnlyOnePendingOrderPerGuestAtDatabaseLevel() {
        UUID sessionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO guest_checkout_sessions (id, token_hash, csrf_hash, created_at, expires_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '1 hour')
                """, sessionId, "c".repeat(64), "d".repeat(64));
        Long firstOrderId = null;
        try {
            firstOrderId = insertGuestPendingOrder(sessionId, "first");
            assertThrows(DataIntegrityViolationException.class, () -> insertGuestPendingOrder(sessionId, "second"));
        } finally {
            jdbc.update("DELETE FROM customer_orders WHERE guest_session_id = ?", sessionId);
            jdbc.update("DELETE FROM guest_checkout_sessions WHERE id = ?", sessionId);
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

    private Long insertGuestPendingOrder(UUID sessionId, String key) {
        return jdbc.queryForObject("""
                INSERT INTO customer_orders (
                    guest_session_id, guest_access_token_hash, buyer_first_name, buyer_last_name, buyer_email,
                    buyer_phone, buyer_document_number, status, subtotal, total, reservation_expires_at,
                    currency, payment_status, fulfillment_status, payment_method, idempotency_key, request_hash
                ) VALUES (?, ?, 'Guest', 'Buyer', 'guest@example.com', '3515550101', '12345678',
                    'PENDING_PAYMENT', 100, 100, CURRENT_TIMESTAMP + INTERVAL '10 minutes', 'ARS',
                    'PENDING', 'PENDING', 'MERCADO_PAGO', ?, ?)
                RETURNING id
                """, Long.class, sessionId, "e".repeat(64), key, "f".repeat(64));
    }

    @Test
    void accountEmailLockSerializesConcurrentIdentityDecisions() throws Exception {
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> transactions.executeWithoutResult(status -> {
                accountEmailLock.lock("ada@example.com");
                firstLocked.countDown();
                await(releaseFirst);
            }));
            assertTrue(firstLocked.await(5, TimeUnit.SECONDS));
            var second = executor.submit(() -> transactions.executeWithoutResult(status -> {
                secondStarted.countDown();
                accountEmailLock.lock("ada@example.com");
            }));
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS));

            Thread.sleep(200);
            assertFalse(second.isDone());
            releaseFirst.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        } finally {
            releaseFirst.countDown();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
