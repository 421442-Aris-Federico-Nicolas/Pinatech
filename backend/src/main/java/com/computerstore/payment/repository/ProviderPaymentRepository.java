package com.computerstore.payment.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.computerstore.payment.domain.ProviderPaymentRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProviderPaymentRepository extends JpaRepository<ProviderPaymentRecord, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from ProviderPaymentRecord payment where payment.providerPaymentId = :paymentId")
    Optional<ProviderPaymentRecord> findByProviderPaymentIdForUpdate(@Param("paymentId") String paymentId);

    boolean existsByAttemptOrderIdAndFundsOrderTrue(Long orderId);

    @Query(value = """
            SELECT * FROM provider_payments
            WHERE refund_status IN ('PENDING', 'REJECTED')
              AND (next_retry_at IS NULL OR next_retry_at <= :now)
              AND (lease_until IS NULL OR lease_until < :now)
            ORDER BY COALESCE(next_retry_at, created_at), id
            LIMIT 50
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<ProviderPaymentRecord> lockRefundsDue(@Param("now") Instant now);
}
