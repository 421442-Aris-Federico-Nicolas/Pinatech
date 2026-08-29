package com.computerstore.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        String status,
        String paymentStatus,
        String fulfillmentStatus,
        String currency,
        String paymentMethod,
        String deliveryMethod,
        String fulfillmentMethod,
        PickupLocationResponse pickupLocation,
        BigDecimal subtotal,
        BigDecimal paymentSurcharge,
        BigDecimal paymentDiscount,
        BigDecimal total,
        Instant createdAt,
        Instant reservationExpiresAt,
        String customerName,
        String customerEmail,
        List<Item> items
) {
    public record Item(
            Long productId,
            Long variantId,
            String productName,
            String colorName,
            String colorHex,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal subtotal
    ) {
    }
}
