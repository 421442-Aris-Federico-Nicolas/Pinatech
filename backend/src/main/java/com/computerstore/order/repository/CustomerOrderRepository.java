package com.computerstore.order.repository;
import com.computerstore.order.domain.CustomerOrder;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {
    List<CustomerOrder> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<CustomerOrder> findAllByOrderByCreatedAtDesc();

    Optional<CustomerOrder> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM customer_orders customer_order JOIN order_items item ON item.order_id = customer_order.id WHERE item.variant_id = :variantId AND customer_order.status NOT IN ('DELIVERED', 'CANCELLED'))", nativeQuery = true)
    boolean existsActiveByVariantId(@Param("variantId") Long variantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select customerOrder from CustomerOrder customerOrder where customerOrder.id = :id")
    Optional<CustomerOrder> findByIdForUpdate(@Param("id") Long id);

    @Query(value = """
            SELECT id
            FROM customer_orders
            WHERE status = 'PENDING_PAYMENT' AND reservation_expires_at <= :now
            ORDER BY id
            FOR UPDATE SKIP LOCKED
            LIMIT 1
            """, nativeQuery = true)
    Optional<Long> findNextExpiredPendingIdForUpdate(@Param("now") Instant now);
}
