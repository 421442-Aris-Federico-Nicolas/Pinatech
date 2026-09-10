package com.computerstore.home.dto;

import com.computerstore.home.domain.HomeHeroImageDevice;

public record AdminHomeHeroImageResponse(Long id, HomeHeroImageDevice device, String url,
        int width, int height, String originalFilename) {}