package com.computerstore.catalog.dto;

public record ProductVariantResponse(Long id, String colorName, String colorHex, Long imageId, boolean inStock, int availableQuantity) {}
