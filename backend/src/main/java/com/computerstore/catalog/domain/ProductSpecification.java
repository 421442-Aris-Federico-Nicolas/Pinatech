package com.computerstore.catalog.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "product_specifications")
public class ProductSpecification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "product_id", nullable = false) private Product product;
    @Column(name = "group_name", nullable = false, length = 100) private String groupName;
    @Column(nullable = false, length = 100) private String name;
    @Column(nullable = false, length = 500) private String value;
    @Column(name = "is_highlighted", nullable = false) private boolean highlighted;
    @Column(name = "display_order", nullable = false) private int displayOrder;

    protected ProductSpecification() {}

    public ProductSpecification(Product product, String groupName, String name, String value, boolean highlighted, int displayOrder) {
        this.product = product;
        this.groupName = groupName;
        this.name = name;
        this.value = value;
        this.highlighted = highlighted;
        this.displayOrder = displayOrder;
    }

    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public String getGroupName() { return groupName; }
    public String getName() { return name; }
    public String getValue() { return value; }
    public boolean isHighlighted() { return highlighted; }
    public int getDisplayOrder() { return displayOrder; }
}
