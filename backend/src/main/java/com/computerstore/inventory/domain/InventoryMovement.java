package com.computerstore.inventory.domain;

import java.time.Instant;
import com.computerstore.catalog.domain.Product;
import com.computerstore.order.domain.CustomerOrder;
import com.computerstore.user.domain.UserAccount;
import jakarta.persistence.*;

@Entity @Table(name = "inventory_movements")
public class InventoryMovement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "product_id", nullable = false) private Product product;
    @Column(name = "movement_type", nullable = false, length = 20) private String movementType;
    @Column(nullable = false) private int quantity;
    @Column(nullable = false, length = 500) private String reason;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "created_by_user_id") private UserAccount createdBy;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "order_id") private CustomerOrder order;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected InventoryMovement() {}
    public InventoryMovement(Product product, int quantity, String reason, UserAccount createdBy) { this.product=product; this.movementType="ADJUSTMENT"; this.quantity=Math.abs(quantity); this.reason=reason; this.createdBy=createdBy; this.createdAt=Instant.now(); }
    private InventoryMovement(Product product, String movementType, int quantity, String reason, CustomerOrder order) { this.product=product; this.movementType=movementType; this.quantity=quantity; this.reason=reason; this.order=order; this.createdAt=Instant.now(); }
    public static InventoryMovement reservation(Product product, int quantity, CustomerOrder order) { return new InventoryMovement(product,"RESERVATION",quantity,"Stock reserved for order",order); }
    public static InventoryMovement release(Product product, int quantity, CustomerOrder order) { return new InventoryMovement(product,"RELEASE",quantity,"Order reservation released",order); }
    public static InventoryMovement consumption(Product product, int quantity, CustomerOrder order) { return new InventoryMovement(product,"CONSUMPTION",quantity,"Order reservation consumed",order); }
    public static InventoryMovement returnedFromCancelledOrder(Product product, int quantity, CustomerOrder order) { return new InventoryMovement(product,"RETURN",quantity,"Stock restored after order cancellation",order); }
}
