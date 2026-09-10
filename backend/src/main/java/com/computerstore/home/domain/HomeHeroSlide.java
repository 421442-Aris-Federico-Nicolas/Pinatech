package com.computerstore.home.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "home_hero_slides")
public class HomeHeroSlide {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "display_order", nullable = false)
    private int displayOrder;
    @Column(name = "is_active", nullable = false)
    private boolean active;
    @Column(nullable = false, length = 100)
    private String eyebrow;
    @Column(nullable = false, length = 150)
    private String title;
    @Column(nullable = false, length = 150)
    private String accent;
    @Column(nullable = false, length = 500)
    private String description;
    @Column(nullable = false, length = 200)
    private String link;
    @Column(name = "link_label", nullable = false, length = 100)
    private String linkLabel;
    @Column(name = "show_login_link", nullable = false)
    private boolean showLoginLink;
    @Column(name = "alt_text", nullable = false, length = 200)
    private String altText;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "slide", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    @Fetch(FetchMode.SUBSELECT)
    private List<HomeHeroImage> images = new ArrayList<>();

    protected HomeHeroSlide() {}

    public HomeHeroSlide(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public void configure(String eyebrow, String title, String accent, String description, String link,
            String linkLabel, boolean showLoginLink, String altText, boolean active) {
        this.eyebrow = eyebrow;
        this.title = title;
        this.accent = accent;
        this.description = description;
        this.link = link;
        this.linkLabel = linkLabel;
        this.showLoginLink = showLoginLink;
        this.altText = altText;
        this.active = active;
    }

    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
    public void setActive(boolean active) { this.active = active; }
    public void addImage(HomeHeroImage image) { images.add(image); }
    public void removeImage(HomeHeroImage image) { images.remove(image); }

    @PrePersist
    void created() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void updated() { updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public int getDisplayOrder() { return displayOrder; }
    public boolean isActive() { return active; }
    public String getEyebrow() { return eyebrow; }
    public String getTitle() { return title; }
    public String getAccent() { return accent; }
    public String getDescription() { return description; }
    public String getLink() { return link; }
    public String getLinkLabel() { return linkLabel; }
    public boolean isShowLoginLink() { return showLoginLink; }
    public String getAltText() { return altText; }
    public List<HomeHeroImage> getImages() { return images; }
}
