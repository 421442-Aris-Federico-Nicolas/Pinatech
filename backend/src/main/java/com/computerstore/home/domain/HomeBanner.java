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
@Table(name = "home_section_banners")
public class HomeBanner {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", nullable = false)
    private HomeSection section;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HomeBannerDevice device;
    @Column(name = "external_url", length = 2048)
    private String externalUrl;
    @Column(name = "storage_key", unique = true, length = 36)
    private String storageKey;
    @Column(name = "original_filename", length = 255)
    private String originalFilename;
    @Column(name = "content_type", length = 50)
    private String contentType;
    @Column(name = "size_bytes")
    private Long sizeBytes;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected HomeBanner() {}

    public HomeBanner(HomeSection section, HomeBannerDevice device, LocalImageStorage.StoredImage stored) {
        this.section = section;
        this.device = device;
        this.storageKey = stored.storageKey();
        this.originalFilename = stored.originalFilename();
        this.contentType = stored.contentType();
        this.sizeBytes = stored.sizeBytes();
    }

    public Long getId() { return id; }
    public HomeSection getSection() { return section; }
    public HomeBannerDevice getDevice() { return device; }
    public String getExternalUrl() { return externalUrl; }
    public String getStorageKey() { return storageKey; }
    public String getOriginalFilename() { return originalFilename; }
    public String getContentType() { return contentType; }
    public Long getSizeBytes() { return sizeBytes; }
}
