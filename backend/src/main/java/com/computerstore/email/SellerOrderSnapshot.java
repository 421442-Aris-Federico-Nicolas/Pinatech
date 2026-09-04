package com.computerstore.email;

import com.computerstore.order.domain.CustomerOrder;
import com.computerstore.order.domain.DeliveryAddressSnapshot;
import com.computerstore.order.domain.PickupLocationSnapshot;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record SellerOrderSnapshot(
        Long orderId,
        Instant orderDate,
        Instant eventDate,
        String orderStatus,
        String paymentMethod,
        String paymentStatus,
        String currency,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal surcharge,
        BigDecimal shipping,
        BigDecimal total,
        String customerName,
        String customerEmail,
        String customerPhone,
        String fulfillmentMethod,
        String fulfillmentStatus,
        String deliveryMethod,
        Delivery delivery,
        Pickup pickup,
        List<Item> items
) {
    public SellerOrderSnapshot {
        items = List.copyOf(items);
    }

    public static SellerOrderSnapshot from(CustomerOrder order, Instant eventDate) {
        var user = order.getUser();
        String customerName = (user.getFirstName() + " " + user.getLastName()).trim();
        return new SellerOrderSnapshot(
                order.getId(),
                order.getCreatedAt(),
                eventDate,
                order.getStatus().name(),
                order.getPaymentMethod().name(),
                order.getPaymentStatus().name(),
                order.getCurrency(),
                order.getSubtotal(),
                order.getPaymentDiscount(),
                order.getPaymentSurcharge(),
                order.getShippingCost(),
                order.getTotal(),
                customerName,
                user.getEmail(),
                user.getPhone(),
                order.getFulfillmentMethod() == null ? null : order.getFulfillmentMethod().name(),
                order.getFulfillmentStatus().name(),
                order.getDeliveryMethod(),
                Delivery.from(order.getDeliveryAddress()),
                Pickup.from(order.getPickupLocation()),
                order.getItems().stream().map(item -> new Item(
                        item.getProductName(),
                        item.getVariantColorName(),
                        item.getVariantColorHex(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getSubtotal())).toList());
    }

    public record Delivery(
            String recipientName,
            String street,
            String streetNumber,
            String floorApartment,
            String locality,
            String province,
            String postalCode,
            String reference
    ) {
        static Delivery from(DeliveryAddressSnapshot delivery) {
            if (delivery == null) return null;
            return new Delivery(delivery.getRecipientName(), delivery.getStreet(), delivery.getStreetNumber(),
                    delivery.getFloorApartment(), delivery.getLocality(), delivery.getProvince(),
                    delivery.getPostalCode(), delivery.getReference());
        }
    }

    public record Pickup(
            String code,
            String name,
            List<String> addressLines,
            String locality,
            String provinceCode,
            String postalCode,
            String instructions,
            String hours
    ) {
        public Pickup {
            addressLines = List.copyOf(addressLines);
        }

        static Pickup from(PickupLocationSnapshot pickup) {
            if (pickup == null) return null;
            return new Pickup(pickup.getCode(), pickup.getName(), pickup.getAddressLines(), pickup.getLocality(),
                    pickup.getProvinceCode(), pickup.getPostalCode(), pickup.getInstructions(), pickup.getHours());
        }
    }

    public record Item(
            String product,
            String color,
            String colorHex,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal subtotal
    ) {
    }
}
