package com.computerstore.inventory.dto;
public record InventoryResponse(Long productId, int availableQuantity, int reservedQuantity) {}
