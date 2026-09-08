package com.computerstore.home.dto;

import com.computerstore.catalog.dto.ProductListItemResponse;
import com.computerstore.home.domain.HomeSectionMode;
import com.computerstore.home.domain.HomeSectionSort;

import java.util.List;

public record HomeSectionResponse(Long id, int displayOrder, String eyebrow, String title,
        String description, String buttonLabel, HomeSectionMode mode, int productLimit,
        HomeSectionSort sort, String bannerDesktopUrl, String bannerMobileUrl,
        List<HomeCategoryResponse> categories, List<ProductListItemResponse> products) {}
