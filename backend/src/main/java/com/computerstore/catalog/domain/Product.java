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
    @Column(name = "shipping_weight_grams") private Integer shippingWeightGrams;
    @Column(name = "shipping_height_cm") private Integer shippingHeightCm;
    @Column(name = "shipping_width_cm") private Integer shippingWidthCm;
    @Column(name = "shipping_length_cm") private Integer shippingLengthCm;
    @Column(name = "shipping_classification_id") private Integer shippingClassificationId;
    @Column(name = "must_keep_vertical", nullable = false) private boolean mustKeepVertical;
    @Column(name = "is_active", nullable = false) private boolean active = true;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected Product() {}
    public Product(String name, String slug, String description, BigDecimal price, Category category, Brand brand,
            Integer shippingWeightGrams, Integer shippingHeightCm, Integer shippingWidthCm, Integer shippingLengthCm,
            Integer shippingClassificationId, boolean mustKeepVertical) {
        this.name=name; this.slug=slug; this.description=description; this.price=price; this.category=category; this.brand=brand;
        updateShippingData(shippingWeightGrams, shippingHeightCm, shippingWidthCm, shippingLengthCm,
                shippingClassificationId, mustKeepVertical);
    }
    public void update(String name, String slug, String description, BigDecimal price, Category category, Brand brand,
            Integer shippingWeightGrams, Integer shippingHeightCm, Integer shippingWidthCm, Integer shippingLengthCm,
            Integer shippingClassificationId, boolean mustKeepVertical) {
        this.name=name; this.slug=slug; this.description=description; this.price=price; this.category=category; this.brand=brand;
        updateShippingData(shippingWeightGrams, shippingHeightCm, shippingWidthCm, shippingLengthCm,
                shippingClassificationId, mustKeepVertical);
    }
    private void updateShippingData(Integer weightGrams, Integer heightCm, Integer widthCm, Integer lengthCm,
            Integer classificationId, boolean keepVertical) {
        this.shippingWeightGrams = weightGrams;
        this.shippingHeightCm = heightCm;
        this.shippingWidthCm = widthCm;
        this.shippingLengthCm = lengthCm;
        this.shippingClassificationId = classificationId;
        this.mustKeepVertical = keepVertical;
    }
    public boolean hasCompleteShippingData() { return shippingWeightGrams != null && shippingHeightCm != null && shippingWidthCm != null && shippingLengthCm != null && shippingClassificationId != null; }
    public void deactivate() { this.active = false; }
    @PrePersist void onCreate() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
    public Long getId() { return id; } public String getName() { return name; } public String getSlug() { return slug; }
    public String getDescription() { return description; } public BigDecimal getPrice() { return price; }
    public Category getCategory() { return category; } public Brand getBrand() { return brand; } public boolean isActive() { return active; }
    public Integer getShippingWeightGrams() { return shippingWeightGrams; }
    public Integer getShippingHeightCm() { return shippingHeightCm; }
    public Integer getShippingWidthCm() { return shippingWidthCm; }
    public Integer getShippingLengthCm() { return shippingLengthCm; }
    public Integer getShippingClassificationId() { return shippingClassificationId; }
    public boolean isMustKeepVertical() { return mustKeepVertical; }
}
