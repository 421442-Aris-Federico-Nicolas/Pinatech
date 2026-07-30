package com.computerstore.inventory.dto;
public record InventoryResponse(Long productId, Long variantId, String colorName, String colorHex, int availableQuantity, int reservedQuantity) {}
