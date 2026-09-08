package com.computerstore.home.domain;

import com.computerstore.catalog.domain.Category;
import com.computerstore.catalog.domain.Product;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "home_sections")
public class HomeSection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "display_order", nullable = false)
    private int displayOrder;
    @Column(length = 100)
    private String eyebrow;
    @Column(nullable = false, length = 150)
    private String title;
    @Column(length = 1000)
    private String description;
    @Column(name = "button_label", length = 100)
    private String buttonLabel;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HomeSectionMode mode;
    @Column(name = "product_limit", nullable = false)
    private int productLimit;
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private HomeSectionSort sort;
    @Column(name = "is_active", nullable = false)
    private boolean active;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "home_section_categories",
            joinColumns = @JoinColumn(name = "section_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id"))
    @Fetch(FetchMode.SUBSELECT)
    private Set<Category> categories = new LinkedHashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "home_section_products",
            joinColumns = @JoinColumn(name = "section_id"),
            inverseJoinColumns = @JoinColumn(name = "product_id"))
    @OrderColumn(name = "display_order")
    @Fetch(FetchMode.SUBSELECT)
    private List<Product> products = new ArrayList<>();

    @OneToMany(mappedBy = "section", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    @Fetch(FetchMode.SUBSELECT)
    private List<HomeBanner> banners = new ArrayList<>();

    protected HomeSection() {}

    public HomeSection(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public void configure(String eyebrow, String title, String description, String buttonLabel,
            HomeSectionMode mode, int productLimit, HomeSectionSort sort, boolean active,
            List<Category> categories, List<Product> products) {
        this.eyebrow = eyebrow;
        this.title = title;
        this.description = description;
        this.buttonLabel = buttonLabel;
        this.mode = mode;
        this.productLimit = productLimit;
        this.sort = sort;
        this.active = active;
        this.categories.clear();
        this.categories.addAll(categories);
        this.products.clear();
        this.products.addAll(products);
    }

    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
    public void addBanner(HomeBanner banner) { banners.add(banner); }
    public void removeBanner(HomeBanner banner) { banners.remove(banner); }

    @PrePersist
    void created() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void updated() { updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public int getDisplayOrder() { return displayOrder; }
    public String getEyebrow() { return eyebrow; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getButtonLabel() { return buttonLabel; }
    public HomeSectionMode getMode() { return mode; }
    public int getProductLimit() { return productLimit; }
    public HomeSectionSort getSort() { return sort; }
    public boolean isActive() { return active; }
    public Set<Category> getCategories() { return categories; }
    public List<Product> getProducts() { return products; }
    public List<HomeBanner> getBanners() { return banners; }
}
