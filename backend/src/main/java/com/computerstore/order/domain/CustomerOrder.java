package com.computerstore.order.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.computerstore.common.exception.InvalidStateTransitionException;
import com.computerstore.user.domain.UserAccount;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "customer_orders")
public class CustomerOrder {

    public static final String DEFAULT_CURRENCY = "ARS";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 30)
    private PaymentStatus paymentStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_status", nullable = false, length = 30)
    private FulfillmentStatus fulfillmentStatus;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(name = "delivery_method", length = 50)
    private String deliveryMethod;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal total;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "reservation_expires_at", nullable = false)
    private Instant reservationExpiresAt;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "request_hash", length = 64)
    private String requestHash;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    protected CustomerOrder() {
    }

    public CustomerOrder(
            UserAccount user,
            List<OrderItem> items,
            BigDecimal total,
            Instant reservationExpiresAt,
            String idempotencyKey,
            String requestHash
    ) {
        this.user = user;
        this.status = OrderStatus.PENDING_PAYMENT;
        this.paymentStatus = PaymentStatus.PENDING;
        this.fulfillmentStatus = FulfillmentStatus.PENDING;
        this.currency = DEFAULT_CURRENCY;
        this.subtotal = total;
        this.total = total;
        this.reservationExpiresAt = Objects.requireNonNull(reservationExpiresAt);
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        items.forEach(this::addItem);
    }

    public CustomerOrder(UserAccount user, List<OrderItem> items, BigDecimal total) {
        this(user, items, total, Instant.now().plusSeconds(900), null, null);
    }

    private void addItem(OrderItem item) {
        item.setOrder(this);
        items.add(item);
    }

    @PrePersist
    void created() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void updated() {
        updatedAt = Instant.now();
    }

    public boolean isReservationExpired(Instant now) {
        return status == OrderStatus.PENDING_PAYMENT && !reservationExpiresAt.isAfter(now);
    }

    public boolean hasReservedStock() {
        return status == OrderStatus.PENDING_PAYMENT || status == OrderStatus.PAID;
    }

    public void transitionTo(OrderStatus target) {
        if (target == status) {
            return;
        }
        boolean valid = switch (status) {
            case PENDING_PAYMENT -> target == OrderStatus.PAID || target == OrderStatus.CANCELLED;
            case PAID -> target == OrderStatus.PREPARING;
            case PREPARING -> target == OrderStatus.READY || target == OrderStatus.CANCELLED;
            case READY -> target == OrderStatus.DELIVERED || target == OrderStatus.CANCELLED;
            default -> false;
        };
        if (!valid) {
            throw new InvalidStateTransitionException("The requested order status transition is not allowed.");
        }

        status = target;
        switch (target) {
            case PAID -> paymentStatus = PaymentStatus.APPROVED;
            case PREPARING -> fulfillmentStatus = FulfillmentStatus.PREPARING;
            case READY -> fulfillmentStatus = FulfillmentStatus.READY;
            case DELIVERED -> fulfillmentStatus = FulfillmentStatus.DELIVERED;
            case CANCELLED -> {
                fulfillmentStatus = FulfillmentStatus.CANCELLED;
                if (paymentStatus == PaymentStatus.PENDING) {
                    paymentStatus = PaymentStatus.CANCELLED;
                }
            }
            default -> {
            }
        }
    }

    public void expire() {
        if (status != OrderStatus.PENDING_PAYMENT) {
            throw new InvalidStateTransitionException("Only a pending payment order can expire.");
        }
        status = OrderStatus.CANCELLED;
        paymentStatus = PaymentStatus.EXPIRED;
        fulfillmentStatus = FulfillmentStatus.CANCELLED;
    }

    public void approveMercadoPagoPayment() {
        transitionTo(OrderStatus.PAID);
        paymentMethod = "MERCADO_PAGO";
    }

    public void markPaymentRefundPending() {
        paymentStatus = PaymentStatus.REFUND_PENDING;
        paymentMethod = "MERCADO_PAGO";
    }

    public void markPaymentRefunded() {
        paymentStatus = PaymentStatus.REFUNDED;
        paymentMethod = "MERCADO_PAGO";
    }

    public Long getId() { return id; }
    public OrderStatus getStatus() { return status; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public FulfillmentStatus getFulfillmentStatus() { return fulfillmentStatus; }
    public String getCurrency() { return currency; }
    public String getPaymentMethod() { return paymentMethod; }
    public String getDeliveryMethod() { return deliveryMethod; }
    public BigDecimal getTotal() { return total; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReservationExpiresAt() { return reservationExpiresAt; }
    public String getRequestHash() { return requestHash; }
    public List<OrderItem> getItems() { return List.copyOf(items); }
    public UserAccount getUser() { return user; }
}
