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
                order.getPaymentMethod().name(),
                order.getDeliveryMethod(),
                order.getFulfillmentMethod() == null ? null : order.getFulfillmentMethod().name(),
                order.getPickupLocation() == null ? null : PickupLocationResponse.from(order.getPickupLocation()),
                order.getSubtotal(),
                order.getShippingCost(),
                order.getPaymentSurcharge(),
                order.getPaymentDiscount(),
                order.getTotal(),
                order.getCreatedAt(),
                order.getReservationExpiresAt(),
                order.getUser().getFirstName() + " " + order.getUser().getLastName(),
                order.getUser().getEmail(),
                order.getItems().stream()
                        .map(item -> new OrderResponse.Item(
                                item.getVariant().getProduct().getId(),
                                item.getVariant().getId(),
                                item.getProductName(),
                                item.getVariantColorName(),
                                item.getVariantColorHex(),
                                item.getUnitPrice(),
                                item.getQuantity(),
                                item.getSubtotal()))
                        .toList(),
                delivery(order),
                shipment(order)
        );
    }

    private static OrderResponse.DeliveryAddress delivery(CustomerOrder order) {
        var value = order.getDeliveryAddress();
        return value == null ? null : new OrderResponse.DeliveryAddress(value.getRecipientName(), value.getStreet(),
                value.getStreetNumber(), value.getFloorApartment(), value.getLocality(), value.getProvince(),
                value.getProvinceCode(), value.getPostalCode(), value.getCountryCode(), value.getReference());
    }

    private static OrderResponse.ShipmentSummary shipment(CustomerOrder order) {
        var value = order.getShipment();
        return value == null ? null : new OrderResponse.ShipmentSummary(value.getStatus().name(), value.getRawStatus(),
                value.getRawSubstatus(), order.getShippingCarrierName(), value.getCarrierTrackingId(),
                value.getTrackingUrl(), value.getEstimatedDeliveryAt() == null
                        ? order.getShippingEta() : value.getEstimatedDeliveryAt(), value.isIncident());
    }
}
