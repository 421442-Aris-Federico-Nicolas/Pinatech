package com.computerstore.payment.repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

import com.computerstore.payment.domain.PaymentAttempt;
import com.computerstore.payment.domain.PaymentAttemptStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {
    @Query("""
            select attempt from PaymentAttempt attempt
            where attempt.order.id = :orderId
              and attempt.status in :statuses
            order by attempt.createdAt desc
            """)
    List<PaymentAttempt> findActiveByOrderId(
            @Param("orderId") Long orderId,
            @Param("statuses") List<PaymentAttemptStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select attempt from PaymentAttempt attempt where attempt.publicId = :publicId")
    Optional<PaymentAttempt> findByPublicIdForUpdate(@Param("publicId") UUID publicId);

    @Query(value = """
            SELECT * FROM payment_attempts
            WHERE preference_id IS NOT NULL
              AND expires_at >= :cutoff
              AND (reconciliation_next_retry_at IS NULL OR reconciliation_next_retry_at <= :now)
              AND (reconciliation_lease_until IS NULL OR reconciliation_lease_until < :now)
            ORDER BY COALESCE(reconciliation_next_retry_at, updated_at), id
            LIMIT 25
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<PaymentAttempt> lockReconciliationsDue(
            @Param("now") java.time.Instant now,
            @Param("cutoff") java.time.Instant cutoff);
}
