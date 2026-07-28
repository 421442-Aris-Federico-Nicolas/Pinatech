package com.computerstore.catalog.dto;
import java.math.BigDecimal;
public record ProductSummaryResponse(Long id, String name, String slug, String description, BigDecimal price, Long categoryId, String categoryName, Long brandId, String brandName) {}
