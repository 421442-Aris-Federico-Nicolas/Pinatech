package com.computerstore.catalog.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProductListItemResponse(Long id, String name, String slug, BigDecimal price,
        Long categoryId, String categoryName, Long brandId, String brandName,
        List<ProductImageResponse> images, boolean inStock) {
    public ProductListItemResponse(Long id, String name, String slug, BigDecimal price,
            Long categoryId, String categoryName, Long brandId, String brandName,
            Long imageId, String imageUrl, String storageKey, String altText,
            String originalFilename, Integer displayOrder, boolean inStock) {
        this(id, name, slug, price, categoryId, categoryName, brandId, brandName,
                imageId == null ? List.of() : List.of(new ProductImageResponse(imageId,
                        storageKey == null ? imageUrl : "/api/products/images/" + imageId + "/thumbnail",
                        altText, originalFilename, displayOrder)), inStock);
    }
}
