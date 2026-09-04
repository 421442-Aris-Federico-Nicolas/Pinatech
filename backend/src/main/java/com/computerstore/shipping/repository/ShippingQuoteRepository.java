package com.computerstore.shipping.repository;

import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import com.computerstore.shipping.domain.ShippingQuote;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ShippingQuoteRepository extends JpaRepository<ShippingQuote, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select quote from ShippingQuote quote where quote.id = :id")
    Optional<ShippingQuote> findByIdForUpdate(@Param("id") UUID id);
    @Modifying
    @Query(value = "delete from shipping_quotes where consumed_order_id is null and expires_at < :cutoff", nativeQuery = true)
    int deleteExpiredUnconsumed(@Param("cutoff") Instant cutoff);
}
