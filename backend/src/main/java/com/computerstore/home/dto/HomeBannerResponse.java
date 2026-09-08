package com.computerstore.home.dto;

import com.computerstore.home.domain.HomeBannerDevice;

public record HomeBannerResponse(Long id, HomeBannerDevice device, String url) {}
