package com.computerstore.catalog.domain;

import java.math.BigDecimal;
import java.time.Instant;
import jakarta.persistence.*;

@Entity
@Table(name = "products")
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 150) private String name;
    @Column(nullable = false, unique = true, length = 180) private String slug;
    @Column(nullable = false, length = 2000) private String description;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal price;
    @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "category_id", nullable = false) private Category category;
    @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "brand_id", nullable = false) private Brand brand;
    @Column(name = "is_active", nullable = false) private boolean active = true;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected Product() {}
    public Product(String name, String slug, String description, BigDecimal price, Category category, Brand brand) { this.name=name; this.slug=slug; this.description=description; this.price=price; this.category=category; this.brand=brand; }
    public void update(String name, String slug, String description, BigDecimal price, Category category, Brand brand) { this.name=name; this.slug=slug; this.description=description; this.price=price; this.category=category; this.brand=brand; }
    public void deactivate() { this.active = false; }
    @PrePersist void onCreate() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
    public Long getId() { return id; } public String getName() { return name; } public String getSlug() { return slug; }
    public String getDescription() { return description; } public BigDecimal getPrice() { return price; }
    public Category getCategory() { return category; } public Brand getBrand() { return brand; } public boolean isActive() { return active; }
}
