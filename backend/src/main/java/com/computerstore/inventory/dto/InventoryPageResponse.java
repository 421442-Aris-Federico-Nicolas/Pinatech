package com.computerstore.inventory.dto;

import com.computerstore.inventory.domain.Inventory;

public record InventoryPageResponse(Long productId, Long variantId, String colorName, String colorHex,
        int availableQuantity, int reservedQuantity, String productName, String brandName) {
    public static InventoryPageResponse from(Inventory inventory) {
        var variant = inventory.getVariant();
        var product = variant.getProduct();
        return new InventoryPageResponse(product.getId(), variant.getId(), variant.getColorName(),
                variant.getColorHex(), inventory.getAvailableQuantity(), inventory.getReservedQuantity(),
                product.getName(), product.getBrand() == null ? null : product.getBrand().getName());
    }
}
