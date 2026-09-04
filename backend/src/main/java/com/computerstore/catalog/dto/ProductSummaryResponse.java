package com.computerstore.catalog.dto;
import java.math.BigDecimal;
import java.util.List;
public record ProductSummaryResponse(Long id, String name, String slug, String description, BigDecimal price,
        Long categoryId, String categoryName, Long brandId, String brandName,
        Integer shippingWeightGrams, Integer shippingHeightCm, Integer shippingWidthCm, Integer shippingLengthCm,
        Integer shippingClassificationId, boolean mustKeepVertical, List<ProductImageResponse> images,
        List<ProductSpecificationResponse> specifications, List<ProductVariantResponse> variants) {}
