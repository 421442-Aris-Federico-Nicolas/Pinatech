package com.computerstore.order.dto;

import java.math.BigDecimal;
import java.util.List;

public record CheckoutCapabilitiesResponse(
        String currency,
        boolean orderRequestsEnabled,
        boolean onlinePaymentsEnabled,
        boolean deliveryQuotesEnabled,
        BigDecimal mercadoPagoSurchargeRate,
        BigDecimal bankTransferDiscountRate,
        List<String> paymentMethods,
        List<String> deliveryMethods,
        List<String> fulfillmentMethods,
        List<PickupLocationResponse> pickupLocations
) {
}
