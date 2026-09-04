package com.computerstore.shipping.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.computerstore.order.domain.CustomerOrder;
import com.computerstore.shipping.gateway.ZipnovaGateway;
import com.computerstore.user.domain.UserAccount;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "shipping_quotes")
public class ShippingQuote {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private UserAccount user;
    @Column(name = "cart_hash", nullable = false, length = 64) private String cartHash;
    @Column(name = "profile_hash", nullable = false, length = 64) private String profileHash;
    @Column(name = "carrier_id", nullable = false) private long carrierId;
    @Column(name = "carrier_name", nullable = false, length = 150) private String carrierName;
    @Column(name = "service_code", nullable = false, length = 100) private String serviceCode;
    @Column(name = "service_name", nullable = false, length = 150) private String serviceName;
    @Column(name = "logistic_type", nullable = false, length = 100) private String logisticType;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal amount;
    @Column(nullable = false, length = 3) private String currency;
    @Column(name = "estimated_delivery_at") private Instant estimatedDeliveryAt;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") private String tags;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "consumed_order_id") private CustomerOrder consumedOrder;

    protected ShippingQuote() {}

    public ShippingQuote(UserAccount user, String cartHash, String profileHash, ZipnovaGateway.QuoteOption option,
                         String tags, Instant createdAt, Instant expiresAt) {
        this.id = UUID.randomUUID(); this.user = user; this.cartHash = cartHash; this.profileHash = profileHash;
        this.carrierId = option.carrierId(); this.carrierName = option.carrierName();
        this.serviceCode = option.serviceCode(); this.serviceName = option.serviceName();
        this.logisticType = option.logisticType(); this.amount = option.priceInclTax(); this.currency = "ARS";
        this.estimatedDeliveryAt = option.estimatedDelivery(); this.tags = tags;
        this.createdAt = createdAt; this.expiresAt = expiresAt;
    }

    public void consume(CustomerOrder order) { this.consumedOrder = order; }
    public UUID getId() { return id; }
    public UserAccount getUser() { return user; }
    public String getCartHash() { return cartHash; }
    public String getProfileHash() { return profileHash; }
    public long getCarrierId() { return carrierId; }
    public String getCarrierName() { return carrierName; }
    public String getServiceCode() { return serviceCode; }
    public String getServiceName() { return serviceName; }
    public String getLogisticType() { return logisticType; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public Instant getEstimatedDeliveryAt() { return estimatedDeliveryAt; }
    public String getTags() { return tags; }
    public Instant getExpiresAt() { return expiresAt; }
    public CustomerOrder getConsumedOrder() { return consumedOrder; }
}
