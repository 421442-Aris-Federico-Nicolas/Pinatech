package com.computerstore.order.dto;

import com.computerstore.order.domain.CustomerOrder;

public final class OrderResponseMapper {

    private OrderResponseMapper() {
    }

    public static OrderResponse toResponse(CustomerOrder order) {
        return new OrderResponse(
                order.getId(),
                order.getStatus().name(),
                order.getPaymentStatus().name(),
                order.getFulfillmentStatus().name(),
                order.getCurrency(),
                order.getPaymentMethod(),
                order.getDeliveryMethod(),
                order.getTotal(),
                order.getCreatedAt(),
                order.getReservationExpiresAt(),
                order.getUser().getFirstName() + " " + order.getUser().getLastName(),
                order.getUser().getEmail(),
                order.getItems().stream()
                        .map(item -> new OrderResponse.Item(
                                item.getProduct().getId(),
                                item.getProductName(),
                                item.getUnitPrice(),
                                item.getQuantity(),
                                item.getSubtotal()))
                        .toList()
        );
    }
}
