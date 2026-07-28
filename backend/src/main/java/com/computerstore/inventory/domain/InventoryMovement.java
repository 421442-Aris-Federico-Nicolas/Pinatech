package com.computerstore.inventory.domain;

import java.time.Instant;
import com.computerstore.catalog.domain.Product;
import com.computerstore.user.domain.UserAccount;
import jakarta.persistence.*;

@Entity @Table(name = "inventory_movements")
public class InventoryMovement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "product_id", nullable = false) private Product product;
    @Column(name = "movement_type", nullable = false, length = 20) private String movementType;
    @Column(nullable = false) private int quantity;
    @Column(nullable = false, length = 500) private String reason;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "created_by_user_id", nullable = false) private UserAccount createdBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected InventoryMovement() {}
    public InventoryMovement(Product product, int quantity, String reason, UserAccount createdBy) { this.product=product; this.movementType="ADJUSTMENT"; this.quantity=Math.abs(quantity); this.reason=reason; this.createdBy=createdBy; this.createdAt=Instant.now(); }
}
