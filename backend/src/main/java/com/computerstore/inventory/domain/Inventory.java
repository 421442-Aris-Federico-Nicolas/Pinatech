package com.computerstore.inventory.domain;

import java.time.Instant;
import com.computerstore.catalog.domain.ProductVariant;
import com.computerstore.common.exception.BusinessRuleException;
import jakarta.persistence.*;

@Entity
@Table(name = "inventory")
public class Inventory {
    @Id @Column(name = "variant_id") private Long variantId;
    @OneToOne(fetch = FetchType.LAZY) @MapsId @JoinColumn(name = "variant_id") private ProductVariant variant;
    @Column(name = "available_quantity", nullable = false) private int availableQuantity;
    @Column(name = "reserved_quantity", nullable = false) private int reservedQuantity;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected Inventory() {}
    public Inventory(ProductVariant variant) { this.variant = variant; this.availableQuantity = 0; this.reservedQuantity = 0; this.updatedAt = Instant.now(); }
    public void adjust(int quantity) { if (availableQuantity + quantity < 0) throw new BusinessRuleException("Stock cannot be negative."); availableQuantity += quantity; updatedAt = Instant.now(); }
    public void reserve(int quantity) { if (availableQuantity < quantity) throw new com.computerstore.common.exception.InsufficientStockException("Insufficient stock for product variant " + variantId + "."); availableQuantity -= quantity; reservedQuantity += quantity; updatedAt = Instant.now(); }
    public void release(int quantity) { if (reservedQuantity < quantity) throw new BusinessRuleException("Reserved stock cannot be negative."); reservedQuantity -= quantity; availableQuantity += quantity; updatedAt = Instant.now(); }
    public void consumeReserved(int quantity) { if (reservedQuantity < quantity) throw new BusinessRuleException("Reserved stock cannot be negative."); reservedQuantity -= quantity; updatedAt = Instant.now(); }
    public void restore(int quantity) { availableQuantity += quantity; updatedAt = Instant.now(); }
    public Long getVariantId() { return variantId; } public int getAvailableQuantity() { return availableQuantity; } public int getReservedQuantity() { return reservedQuantity; }
    public ProductVariant getVariant() { return variant; }
}
