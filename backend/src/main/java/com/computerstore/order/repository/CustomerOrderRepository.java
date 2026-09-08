package com.computerstore.order.repository;
import com.computerstore.order.domain.CustomerOrder;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import com.computerstore.order.domain.OrderStatus;
import com.computerstore.order.domain.PaymentMethod;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.computerstore.order.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {
    // Explicit user subgraphs exclude eager roles; items is the only fetched bag.
    @EntityGraph(attributePaths = {"user.firstName", "user.lastName", "user.email", "items.variant.product.id", "shipment"})
    List<CustomerOrder> findByUserIdOrderByCreatedAtDesc(Long userId);
    @EntityGraph(attributePaths = {"user.firstName", "user.lastName", "user.email", "items.variant.product.id", "shipment"})
    List<CustomerOrder> findAllByOrderByCreatedAtDesc();

    @Query("select o.id from CustomerOrder o where (:status is null or o.status = :status) order by o.createdAt desc, o.id desc")
    Page<Long> findPageIds(@Param("status") OrderStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"user.firstName", "user.lastName", "user.email", "items.variant.product.id", "shipment"})
    @Query("select o from CustomerOrder o where o.id in :ids order by o.createdAt desc, o.id desc")
    List<CustomerOrder> findDetailsByIds(@Param("ids") List<Long> ids);

    @EntityGraph(attributePaths = {"user.firstName", "user.lastName", "user.email", "items.variant.product.id", "shipment"})
    @Query("select o from CustomerOrder o where o.id = :id")
    Optional<CustomerOrder> findDetailsById(@Param("id") Long id);

    interface StatusTotal {
        OrderStatus getStatus();
        PaymentStatus getPaymentStatus();
        long getQuantity();
        BigDecimal getTotal();
    }

    @Query("select o.status as status, o.paymentStatus as paymentStatus, count(o) as quantity, sum(o.total) as total from CustomerOrder o group by o.status, o.paymentStatus")
    List<StatusTotal> summarizeStatuses();

    interface DailyTotal {
        LocalDate getDay();
        BigDecimal getTotal();
    }

    @Query(value = """
            select cast(created_at at time zone 'America/Argentina/Buenos_Aires' as date) as day,
                   sum(total) as total
            from customer_orders
            where payment_status = 'APPROVED' and created_at >= :start and created_at < :end
            group by 1 order by 1
            """, nativeQuery = true)
    List<DailyTotal> summarizeDays(@Param("start") Instant start, @Param("end") Instant end);

    Optional<CustomerOrder> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);
    boolean existsByUserIdAndStatusAndPaymentMethod(Long userId, OrderStatus status, PaymentMethod paymentMethod);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM customer_orders customer_order JOIN order_items item ON item.order_id = customer_order.id WHERE item.variant_id = :variantId AND customer_order.status NOT IN ('DELIVERED', 'CANCELLED'))", nativeQuery = true)
    boolean existsActiveByVariantId(@Param("variantId") Long variantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select customerOrder from CustomerOrder customerOrder where customerOrder.id = :id")
    Optional<CustomerOrder> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select customerOrder from CustomerOrder customerOrder where customerOrder.id = :id and customerOrder.user.id = :userId")
    Optional<CustomerOrder> findByIdAndUserIdForUpdate(@Param("id") Long id, @Param("userId") Long userId);

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
