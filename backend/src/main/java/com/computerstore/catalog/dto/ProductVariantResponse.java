package com.computerstore.catalog.dto;

public record ProductVariantResponse(Long id, String colorName, String colorHex, boolean inStock, int availableQuantity) {}
