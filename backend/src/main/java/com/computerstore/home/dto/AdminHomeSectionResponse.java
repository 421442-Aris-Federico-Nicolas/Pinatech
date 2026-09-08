package com.computerstore.home.dto;

import com.computerstore.catalog.dto.ProductListItemResponse;
import com.computerstore.home.domain.HomeSectionMode;
import com.computerstore.home.domain.HomeSectionSort;

import java.util.List;

public record AdminHomeSectionResponse(Long id, int displayOrder, String eyebrow, String title,
        String description, String buttonLabel, HomeSectionMode mode, int productLimit,
        HomeSectionSort sort, List<Long> categoryIds, List<Long> productIds,
        List<ProductListItemResponse> products, boolean active,
        String bannerDesktopUrl, String bannerMobileUrl) {}
