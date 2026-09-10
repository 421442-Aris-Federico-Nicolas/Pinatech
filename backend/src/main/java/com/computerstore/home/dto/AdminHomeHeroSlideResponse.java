package com.computerstore.home.dto;

import java.util.List;

public record AdminHomeHeroSlideResponse(Long id, int displayOrder, boolean active, String eyebrow,
        String title, String accent, String description, String link, String linkLabel,
        boolean showLoginLink, String altText, List<AdminHomeHeroImageResponse> images) {}