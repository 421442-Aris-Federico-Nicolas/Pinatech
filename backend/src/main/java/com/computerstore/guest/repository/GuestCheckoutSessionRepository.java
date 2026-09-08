package com.computerstore.guest.repository;

import com.computerstore.guest.domain.GuestCheckoutSession;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface GuestCheckoutSessionRepository extends JpaRepository<GuestCheckoutSession, UUID> {
    Optional<GuestCheckoutSession> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from GuestCheckoutSession session where session.tokenHash = :hash")
    Optional<GuestCheckoutSession> findByTokenHashForUpdate(@Param("hash") String hash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from GuestCheckoutSession session where session.id = :id")
    Optional<GuestCheckoutSession> findByIdForUpdate(@Param("id") UUID id);
}
