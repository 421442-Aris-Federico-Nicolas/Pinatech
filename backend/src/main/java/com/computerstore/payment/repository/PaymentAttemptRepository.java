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
    Optional<PaymentAttempt> findByOrderIdAndIdempotencyKey(Long orderId, String idempotencyKey);
    List<PaymentAttempt> findTop50ByStatusOrderByUpdatedAtAsc(PaymentAttemptStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select attempt from PaymentAttempt attempt where attempt.publicId = :publicId")
    Optional<PaymentAttempt> findByPublicIdForUpdate(@Param("publicId") UUID publicId);
}
