package com.computerstore.home.dto;

public record HomeHeroSlideResponse(Long id, int displayOrder, String eyebrow, String title,
        String accent, String description, String link, String linkLabel, boolean showLoginLink,
        String altText, HomeHeroImageResponse desktopImage, HomeHeroImageResponse mobileImage) {}