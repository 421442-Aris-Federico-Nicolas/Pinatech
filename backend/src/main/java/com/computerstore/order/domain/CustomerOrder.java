package com.computerstore.order.domain;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import com.computerstore.user.domain.UserAccount;
import com.computerstore.common.exception.InvalidStateTransitionException;
import jakarta.persistence.*;
@Entity @Table(name="customer_orders") public class CustomerOrder {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="user_id",nullable=false) private UserAccount user;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private OrderStatus status;
 @Column(nullable=false,precision=19,scale=2) private BigDecimal subtotal;
 @Column(nullable=false,precision=19,scale=2) private BigDecimal total;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @Column(name="updated_at",nullable=false) private Instant updatedAt;
 @Column(name="reservation_expires_at",nullable=false) private Instant reservationExpiresAt;
 @Column(name="idempotency_key",length=100) private String idempotencyKey;
 @Column(name="request_hash",length=64) private String requestHash;
 @OneToMany(mappedBy="order",cascade=CascadeType.ALL,orphanRemoval=true) private List<OrderItem> items=new ArrayList<>();
 protected CustomerOrder() {}
 public CustomerOrder(UserAccount user,List<OrderItem> items,BigDecimal total,Instant reservationExpiresAt,String idempotencyKey,String requestHash){this.user=user;this.status=OrderStatus.PENDING_PAYMENT;this.subtotal=total;this.total=total;this.reservationExpiresAt=Objects.requireNonNull(reservationExpiresAt);this.idempotencyKey=idempotencyKey;this.requestHash=requestHash;items.forEach(this::addItem);}
 public CustomerOrder(UserAccount user,List<OrderItem> items,BigDecimal total){this(user,items,total,Instant.now().plusSeconds(900),null,null);}
 private void addItem(OrderItem item){item.setOrder(this);items.add(item);}
 @PrePersist void created(){createdAt=Instant.now();updatedAt=createdAt;} @PreUpdate void updated(){updatedAt=Instant.now();}
 public Long getId(){return id;} public OrderStatus getStatus(){return status;} public BigDecimal getTotal(){return total;} public Instant getCreatedAt(){return createdAt;} public Instant getReservationExpiresAt(){return reservationExpiresAt;} public String getRequestHash(){return requestHash;} public List<OrderItem> getItems(){return List.copyOf(items);} public UserAccount getUser(){return user;}
 public boolean isReservationExpired(Instant now){return status==OrderStatus.PENDING_PAYMENT&&!reservationExpiresAt.isAfter(now);}
 public boolean hasReservedStock(){return status==OrderStatus.PENDING_PAYMENT||status==OrderStatus.PAID;}
 public void transitionTo(OrderStatus target){ if(target==status)return; boolean valid=switch(status){case PENDING_PAYMENT -> target==OrderStatus.PAID||target==OrderStatus.CANCELLED;case PAID -> target==OrderStatus.PREPARING||target==OrderStatus.CANCELLED;case PREPARING -> target==OrderStatus.READY;case READY -> target==OrderStatus.DELIVERED;default -> false;};if(!valid)throw new InvalidStateTransitionException("The requested order status transition is not allowed.");status=target;}
}
