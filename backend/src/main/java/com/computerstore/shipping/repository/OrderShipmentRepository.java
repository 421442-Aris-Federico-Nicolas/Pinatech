package com.computerstore.shipping.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import com.computerstore.shipping.domain.OrderShipment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface OrderShipmentRepository extends JpaRepository<OrderShipment, UUID> {
    Optional<OrderShipment> findByOrderId(Long orderId);
    Optional<OrderShipment> findByProviderShipmentId(Long providerShipmentId);
    boolean existsByOrderId(Long orderId);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select shipment from OrderShipment shipment where shipment.id = :id")
    Optional<OrderShipment> findByIdForUpdate(@Param("id") UUID id);
    @Query(value = """
            SELECT id FROM order_shipments
            WHERE status IN ('PENDING_CREATE','RETRY','CREATING') AND next_attempt_at <= :now
              AND (lease_until IS NULL OR lease_until <= :now)
            ORDER BY created_at, id FOR UPDATE SKIP LOCKED LIMIT 1
            """, nativeQuery = true)
    Optional<UUID> findNextCreationForUpdate(@Param("now") Instant now);
    @Query(value = """
            SELECT id FROM order_shipments
            WHERE provider_shipment_id IS NOT NULL AND next_attempt_at <= :now
              AND ((status IN ('ACTIVE','INCIDENT')
                    AND LOWER(COALESCE(raw_status, '')) NOT IN ('delivered', 'delivered_with_damage', 'cancelled', 'canceled'))
                   OR tracking_sync_pending = TRUE)
              AND (lease_until IS NULL OR lease_until <= :now)
            ORDER BY next_attempt_at, id FOR UPDATE SKIP LOCKED LIMIT 1
            """, nativeQuery = true)
    Optional<UUID> findNextReconciliationForUpdate(@Param("now") Instant now);
    @Query(value = """
            SELECT shipments.id FROM order_shipments shipments
            WHERE shipments.cancellation_scope IS NOT NULL AND shipments.provider_shipment_id IS NOT NULL
              AND (shipments.status IN ('ACTIVE','INCIDENT')
                   OR (shipments.status = 'CANCELLED' AND shipments.cancellation_scope = 'ORDER'
                       AND EXISTS (SELECT 1 FROM customer_orders orders
                                   WHERE orders.id = shipments.order_id AND orders.status <> 'CANCELLED')))
              AND shipments.next_attempt_at <= :now
              AND (shipments.lease_until IS NULL OR shipments.lease_until <= :now)
            ORDER BY shipments.next_attempt_at, shipments.id FOR UPDATE SKIP LOCKED LIMIT 1
            """, nativeQuery = true)
    Optional<UUID> findNextCancellationForUpdate(@Param("now") Instant now);
}
