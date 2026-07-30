package com.computerstore.catalog.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "product_images")
public class ProductImage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "image_url", nullable = false, length = 2048)
    private String imageUrl;

    @Column(name = "alt_text", nullable = false, length = 250)
    private String altText;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "storage_key", unique = true, length = 36)
    private String storageKey;

    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    @Column(name = "content_type", length = 50)
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "created_at")
    private Instant createdAt;

    protected ProductImage() {}

    public ProductImage(Product product, String altText, int displayOrder, String storageKey,
                        String originalFilename, String contentType, long sizeBytes) {
        this.product = product;
        this.imageUrl = "";
        this.altText = altText;
        this.displayOrder = displayOrder;
        this.storageKey = storageKey;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
    }

    @PrePersist
    void created() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public void setContentUrl(String contentUrl) { this.imageUrl = contentUrl; }
    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public String getImageUrl() { return imageUrl; }
    public String getAltText() { return altText; }
    public int getDisplayOrder() { return displayOrder; }
    public String getStorageKey() { return storageKey; }
    public String getOriginalFilename() { return originalFilename; }
    public String getContentType() { return contentType; }
    public Long getSizeBytes() { return sizeBytes; }
}
