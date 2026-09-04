package com.computerstore.shipping.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import com.computerstore.shipping.domain.ShippingWebhookInbox;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ShippingWebhookInboxRepository extends JpaRepository<ShippingWebhookInbox, UUID> {
    boolean existsByPayloadHash(String payloadHash);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select inbox from ShippingWebhookInbox inbox where inbox.id = :id")
    Optional<ShippingWebhookInbox> findByIdForUpdate(@Param("id") UUID id);
    @Query(value = """
            SELECT id FROM shipping_webhook_inbox
            WHERE status IN ('PENDING','PROCESSING') AND next_attempt_at <= :now
              AND (lease_until IS NULL OR lease_until <= :now)
            ORDER BY received_at, id FOR UPDATE SKIP LOCKED LIMIT 1
            """, nativeQuery = true)
    Optional<UUID> findNextForUpdate(@Param("now") Instant now);
    @Modifying
    @Query(value = "delete from shipping_webhook_inbox where status in ('DONE', 'FAILED') and processed_at < :cutoff",
            nativeQuery = true)
    int deleteProcessedBefore(@Param("cutoff") Instant cutoff);
}
