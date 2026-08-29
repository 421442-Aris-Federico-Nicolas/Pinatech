package com.computerstore.email;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EmailOutboxRepository extends JpaRepository<EmailOutboxEntry, UUID> {
    @Query(value = """
            SELECT * FROM email_outbox
            WHERE status IN ('PENDING', 'SENDING') AND next_attempt_at <= :now
              AND (lease_until IS NULL OR lease_until <= :now)
            ORDER BY created_at, id FOR UPDATE SKIP LOCKED LIMIT 1
            """, nativeQuery = true)
    Optional<EmailOutboxEntry> findNextDueForUpdate(@Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select entry from EmailOutboxEntry entry where entry.id = :id")
    Optional<EmailOutboxEntry> findByIdForUpdate(@Param("id") UUID id);
}
