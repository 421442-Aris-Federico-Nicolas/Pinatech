package com.computerstore.home.domain;

import com.computerstore.storage.LocalImageStorage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "home_hero_images")
public class HomeHeroImage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "slide_id", nullable = false)
    private HomeHeroSlide slide;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HomeHeroImageDevice device;
    @Column(name = "storage_key", unique = true, nullable = false, length = 36)
    private String storageKey;
    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;
    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;
    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;
    @Column(nullable = false)
    private Integer width;
    @Column(nullable = false)
    private Integer height;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected HomeHeroImage() {}

    public HomeHeroImage(HomeHeroSlide slide, HomeHeroImageDevice device, LocalImageStorage.StoredImage stored,
            int width, int height) {
        this.slide = slide;
        this.device = device;
        this.storageKey = stored.storageKey();
        this.originalFilename = stored.originalFilename();
        this.contentType = stored.contentType();
        this.sizeBytes = stored.sizeBytes();
        this.width = width;
        this.height = height;
    }

    public Long getId() { return id; }
    public HomeHeroSlide getSlide() { return slide; }
    public HomeHeroImageDevice getDevice() { return device; }
    public String getStorageKey() { return storageKey; }
    public String getOriginalFilename() { return originalFilename; }
    public String getContentType() { return contentType; }
    public Long getSizeBytes() { return sizeBytes; }
    public Integer getWidth() { return width; }
    public Integer getHeight() { return height; }
}