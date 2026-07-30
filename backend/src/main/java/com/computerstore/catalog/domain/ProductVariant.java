package com.computerstore.catalog.domain;

import java.time.Instant;
import jakarta.persistence.*;

@Entity
@Table(name = "product_variants")
public class ProductVariant {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "product_id", nullable = false) private Product product;
    @Column(name = "color_name", nullable = false, length = 100) private String colorName;
    @Column(name = "color_hex", length = 7) private String colorHex;
    @Column(name = "display_order", nullable = false) private int displayOrder;
    @Column(name = "is_active", nullable = false) private boolean active = true;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected ProductVariant() {}

    public ProductVariant(Product product, String colorName, String colorHex, int displayOrder) {
        this.product = product;
        update(colorName, colorHex, displayOrder);
    }

    public void update(String colorName, String colorHex, int displayOrder) {
        this.colorName = colorName;
        this.colorHex = colorHex;
        this.displayOrder = displayOrder;
    }

    public void deactivate() { active = false; }
    @PrePersist void created() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate void updated() { updatedAt = Instant.now(); }
    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public String getColorName() { return colorName; }
    public String getColorHex() { return colorHex; }
    public int getDisplayOrder() { return displayOrder; }
    public boolean isActive() { return active; }
}
