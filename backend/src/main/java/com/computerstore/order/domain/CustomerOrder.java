package com.computerstore.order.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.computerstore.common.exception.InvalidStateTransitionException;
import com.computerstore.user.domain.UserAccount;
import com.computerstore.shipping.domain.OrderShipment;
import com.computerstore.shipping.domain.ShippingQuote;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Embedded;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, updatable = false, length = 50)
    private PaymentMethod paymentMethod;

    @Column(name = "delivery_method", length = 50)
    private String deliveryMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_method", length = 30)
    private FulfillmentMethod fulfillmentMethod;

    @Embedded
    private PickupLocationSnapshot pickupLocation;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "payment_surcharge", nullable = false, precision = 19, scale = 2)
    private BigDecimal paymentSurcharge;

    @Column(name = "payment_discount", nullable = false, precision = 19, scale = 2)
    private BigDecimal paymentDiscount;

    @Column(name = "shipping_cost", nullable = false, precision = 19, scale = 2)
    private BigDecimal shippingCost;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipping_quote_id")
    private ShippingQuote shippingQuote;

    @Column(name = "shipping_carrier_id") private Long shippingCarrierId;
    @Column(name = "shipping_carrier_name", length = 150) private String shippingCarrierName;
    @Column(name = "shipping_service_code", length = 100) private String shippingServiceCode;
    @Column(name = "shipping_service_name", length = 150) private String shippingServiceName;
    @Column(name = "shipping_logistic_type", length = 100) private String shippingLogisticType;
    @Column(name = "shipping_eta") private Instant shippingEta;

    @Embedded
    private DeliveryAddressSnapshot deliveryAddress;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal total;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "reservation_expires_at")
    private Instant reservationExpiresAt;

    @Column(name = "payment_due_at")
    private Instant paymentDueAt;

    @Embedded
    private BankAccountSnapshot bankAccount;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "request_hash", length = 64)
    private String requestHash;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @OneToOne(mappedBy = "order", fetch = FetchType.LAZY)
    private OrderShipment shipment;

    protected CustomerOrder() {
    }

    public CustomerOrder(UserAccount user, List<OrderItem> items, BigDecimal total, Instant reservationExpiresAt,
                         String idempotencyKey, String requestHash) {
        this(user, items, total, BigDecimal.ZERO, BigDecimal.ZERO, PaymentMethod.MERCADO_PAGO,
                reservationExpiresAt, null, null, idempotencyKey, requestHash, null, null);
    }

    public CustomerOrder(UserAccount user, List<OrderItem> items, BigDecimal total, Instant reservationExpiresAt,
                         String idempotencyKey, String requestHash, FulfillmentMethod fulfillmentMethod,
                         PickupLocationSnapshot pickupLocation) {
        this(user, items, total, BigDecimal.ZERO, BigDecimal.ZERO, PaymentMethod.MERCADO_PAGO,
                reservationExpiresAt, null, null, idempotencyKey, requestHash, fulfillmentMethod, pickupLocation);
    }

    public CustomerOrder(UserAccount user, List<OrderItem> items, BigDecimal subtotal, BigDecimal paymentSurcharge,
                         BigDecimal paymentDiscount, PaymentMethod paymentMethod, Instant reservationExpiresAt,
                         Instant paymentDueAt, BankAccountSnapshot bankAccount, String idempotencyKey,
                         String requestHash, FulfillmentMethod fulfillmentMethod,
                         PickupLocationSnapshot pickupLocation) {
        this(user, items, subtotal, paymentSurcharge, paymentDiscount, BigDecimal.ZERO, paymentMethod,
                reservationExpiresAt, paymentDueAt, bankAccount, idempotencyKey, requestHash,
                fulfillmentMethod, pickupLocation, null, null);
    }

    public CustomerOrder(UserAccount user, List<OrderItem> items, BigDecimal subtotal, BigDecimal paymentSurcharge,
                         BigDecimal paymentDiscount, BigDecimal shippingCost, PaymentMethod paymentMethod,
                         Instant reservationExpiresAt, Instant paymentDueAt, BankAccountSnapshot bankAccount,
                         String idempotencyKey, String requestHash, FulfillmentMethod fulfillmentMethod,
                         PickupLocationSnapshot pickupLocation, ShippingQuote shippingQuote,
                         DeliveryAddressSnapshot deliveryAddress) {
        this.user = Objects.requireNonNull(user);
        this.status = OrderStatus.PENDING_PAYMENT;
        this.paymentStatus = PaymentStatus.PENDING;
        this.fulfillmentStatus = FulfillmentStatus.PENDING;
        this.currency = DEFAULT_CURRENCY;
        this.paymentMethod = Objects.requireNonNull(paymentMethod);
        this.subtotal = Objects.requireNonNull(subtotal);
        this.paymentSurcharge = Objects.requireNonNull(paymentSurcharge);
        this.paymentDiscount = Objects.requireNonNull(paymentDiscount);
        this.shippingCost = Objects.requireNonNull(shippingCost);
        if (subtotal.signum() < 0 || paymentSurcharge.signum() < 0
                || paymentDiscount.signum() < 0 || paymentDiscount.compareTo(subtotal) > 0
                || shippingCost.signum() < 0) {
            throw new IllegalArgumentException("Order amounts are invalid.");
        }
        this.total = subtotal.add(shippingCost).add(paymentSurcharge).subtract(paymentDiscount);
        this.reservationExpiresAt = reservationExpiresAt;
        this.paymentDueAt = paymentDueAt;
        this.bankAccount = bankAccount;
        if (paymentMethod == PaymentMethod.MERCADO_PAGO && reservationExpiresAt == null) {
            throw new IllegalArgumentException("Mercado Pago orders require a reservation expiry.");
        }
        if (paymentMethod == PaymentMethod.MERCADO_PAGO && paymentDiscount.signum() != 0) {
            throw new IllegalArgumentException("Mercado Pago orders cannot have a payment discount.");
        }
        if (paymentMethod == PaymentMethod.BANK_TRANSFER
                && (paymentDueAt == null || bankAccount == null || paymentSurcharge.signum() != 0)) {
            throw new IllegalArgumentException("Bank transfer orders require a due date, account snapshot and zero surcharge.");
        }
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.fulfillmentMethod = fulfillmentMethod;
        this.pickupLocation = pickupLocation;
        this.shippingQuote = shippingQuote;
        this.deliveryAddress = deliveryAddress;
        if (fulfillmentMethod == FulfillmentMethod.DELIVERY) {
            if (shippingQuote == null || deliveryAddress == null) {
                throw new IllegalArgumentException("Delivery orders require a quote and address snapshot.");
            }
            this.deliveryMethod = "ZIPNOVA";
            this.shippingCarrierId = shippingQuote.getCarrierId();
            this.shippingCarrierName = shippingQuote.getCarrierName();
            this.shippingServiceCode = shippingQuote.getServiceCode();
            this.shippingServiceName = shippingQuote.getServiceName();
            this.shippingLogisticType = shippingQuote.getLogisticType();
            this.shippingEta = shippingQuote.getEstimatedDeliveryAt();
        } else if (shippingCost.signum() != 0 || shippingQuote != null || deliveryAddress != null) {
            throw new IllegalArgumentException("Pickup orders cannot contain delivery data.");
        }
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
        return status == OrderStatus.PENDING_PAYMENT && reservationExpiresAt != null
                && !reservationExpiresAt.isAfter(now);
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
            case READY -> target == OrderStatus.SHIPPED || target == OrderStatus.DELIVERED || target == OrderStatus.CANCELLED;
            case SHIPPED -> target == OrderStatus.DELIVERED;
            default -> false;
        };
        if (!valid) {
            throw new InvalidStateTransitionException("The requested order status transition is not allowed.");
        }
        if (target == OrderStatus.SHIPPED && fulfillmentMethod != FulfillmentMethod.DELIVERY) {
            throw new InvalidStateTransitionException("Pickup orders cannot be shipped.");
        }
        if (target == OrderStatus.DELIVERED && status == OrderStatus.READY
                && fulfillmentMethod == FulfillmentMethod.DELIVERY) {
            throw new InvalidStateTransitionException("Delivery completion must be reported by Zipnova.");
        }
        if ((target == OrderStatus.PREPARING || target == OrderStatus.READY || target == OrderStatus.SHIPPED
                || target == OrderStatus.DELIVERED)
                && paymentStatus != PaymentStatus.APPROVED) {
            throw new InvalidStateTransitionException("Fulfillment requires an approved payment without disputes.");
        }
        if (fulfillmentMethod == FulfillmentMethod.DELIVERY && fulfillmentStatus == FulfillmentStatus.CANCELLED
                && target != OrderStatus.CANCELLED) {
            throw new InvalidStateTransitionException("A cancelled shipment must be replaced before fulfillment can continue.");
        }

        status = target;
        switch (target) {
            case PAID -> paymentStatus = PaymentStatus.APPROVED;
            case PREPARING -> fulfillmentStatus = FulfillmentStatus.PREPARING;
            case READY -> fulfillmentStatus = FulfillmentStatus.READY;
            case SHIPPED -> fulfillmentStatus = FulfillmentStatus.SHIPPED;
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
        if (paymentMethod != PaymentMethod.MERCADO_PAGO) {
            throw new InvalidStateTransitionException("Mercado Pago cannot approve a bank transfer order.");
        }
        transitionTo(OrderStatus.PAID);
    }

    public void confirmPaymentApproved() {
        if (status == OrderStatus.PAID && paymentMethod == PaymentMethod.MERCADO_PAGO) {
            paymentStatus = PaymentStatus.APPROVED;
        }
    }

    public void submitBankTransferProof() {
        if (paymentMethod != PaymentMethod.BANK_TRANSFER || status != OrderStatus.PENDING_PAYMENT
                || paymentStatus != PaymentStatus.PENDING) {
            throw new InvalidStateTransitionException("This order cannot receive a bank transfer proof.");
        }
        paymentStatus = PaymentStatus.UNDER_REVIEW;
        reservationExpiresAt = null;
    }

    public void approveBankTransfer() {
        if (paymentMethod != PaymentMethod.BANK_TRANSFER || status != OrderStatus.PENDING_PAYMENT
                || paymentStatus != PaymentStatus.UNDER_REVIEW) {
            throw new InvalidStateTransitionException("This bank transfer is not awaiting review.");
        }
        transitionTo(OrderStatus.PAID);
    }

    public void rejectBankTransfer() {
        if (paymentMethod != PaymentMethod.BANK_TRANSFER || status != OrderStatus.PENDING_PAYMENT
                || paymentStatus != PaymentStatus.UNDER_REVIEW) {
            throw new InvalidStateTransitionException("This bank transfer is not awaiting review.");
        }
        status = OrderStatus.CANCELLED;
        paymentStatus = PaymentStatus.REJECTED;
        fulfillmentStatus = FulfillmentStatus.CANCELLED;
    }

    public void markPaymentRefundPending() {
        paymentStatus = PaymentStatus.REFUND_PENDING;
    }

    public void markPaymentRefunded() {
        paymentStatus = PaymentStatus.REFUNDED;
    }

    public void markPaymentInMediation() {
        paymentStatus = PaymentStatus.IN_MEDIATION;
    }

    public void markPaymentChargedBack() {
        paymentStatus = PaymentStatus.CHARGEBACK;
    }

    public void markShipmentCancelled() {
        if (fulfillmentMethod == FulfillmentMethod.DELIVERY
                && status != OrderStatus.CANCELLED && status != OrderStatus.DELIVERED) {
            fulfillmentStatus = FulfillmentStatus.CANCELLED;
        }
    }

    public void markShipmentReplacementPending() {
        if (fulfillmentMethod != FulfillmentMethod.DELIVERY || fulfillmentStatus != FulfillmentStatus.CANCELLED) {
            return;
        }
        fulfillmentStatus = switch (status) {
            case PAID -> FulfillmentStatus.PENDING;
            case PREPARING -> FulfillmentStatus.PREPARING;
            case READY -> FulfillmentStatus.READY;
            case SHIPPED -> FulfillmentStatus.SHIPPED;
            default -> fulfillmentStatus;
        };
    }

    public void markAuthoritativelyShipped() {
        if (fulfillmentMethod != FulfillmentMethod.DELIVERY || paymentStatus != PaymentStatus.APPROVED
                || fulfillmentStatus == FulfillmentStatus.CANCELLED) return;
        if (status == OrderStatus.PREPARING || status == OrderStatus.READY) {
            status = OrderStatus.SHIPPED;
            fulfillmentStatus = FulfillmentStatus.SHIPPED;
        }
    }

    public boolean markAuthoritativelyDelivered() {
        if (fulfillmentMethod != FulfillmentMethod.DELIVERY || paymentStatus != PaymentStatus.APPROVED
                || fulfillmentStatus == FulfillmentStatus.CANCELLED) return false;
        if (status == OrderStatus.PREPARING || status == OrderStatus.READY) markAuthoritativelyShipped();
        if (status == OrderStatus.SHIPPED) {
            status = OrderStatus.DELIVERED;
            fulfillmentStatus = FulfillmentStatus.DELIVERED;
            return true;
        }
        return false;
    }

    public Long getId() { return id; }
    public OrderStatus getStatus() { return status; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public FulfillmentStatus getFulfillmentStatus() { return fulfillmentStatus; }
    public String getCurrency() { return currency; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public String getDeliveryMethod() { return deliveryMethod; }
    public FulfillmentMethod getFulfillmentMethod() { return fulfillmentMethod; }
    public PickupLocationSnapshot getPickupLocation() { return pickupLocation; }
    public BigDecimal getTotal() { return total; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getPaymentSurcharge() { return paymentSurcharge; }
    public BigDecimal getPaymentDiscount() { return paymentDiscount; }
    public BigDecimal getShippingCost() { return shippingCost; }
    public ShippingQuote getShippingQuote() { return shippingQuote; }
    public Long getShippingCarrierId() { return shippingCarrierId; }
    public String getShippingCarrierName() { return shippingCarrierName; }
    public String getShippingServiceCode() { return shippingServiceCode; }
    public String getShippingServiceName() { return shippingServiceName; }
    public String getShippingLogisticType() { return shippingLogisticType; }
    public Instant getShippingEta() { return shippingEta; }
    public DeliveryAddressSnapshot getDeliveryAddress() { return deliveryAddress; }
    public OrderShipment getShipment() { return shipment; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReservationExpiresAt() { return reservationExpiresAt; }
    public Instant getPaymentDueAt() { return paymentDueAt; }
    public BankAccountSnapshot getBankAccount() { return bankAccount; }
    public String getRequestHash() { return requestHash; }
    public List<OrderItem> getItems() { return List.copyOf(items); }
    public UserAccount getUser() { return user; }
}
