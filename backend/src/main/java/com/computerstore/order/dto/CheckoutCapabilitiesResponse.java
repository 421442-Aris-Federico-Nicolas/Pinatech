package com.computerstore.order.dto;

import java.util.List;

public record CheckoutCapabilitiesResponse(
        String currency,
        boolean orderRequestsEnabled,
        boolean onlinePaymentsEnabled,
        boolean deliveryQuotesEnabled,
        List<String> paymentMethods,
        List<String> deliveryMethods,
        List<String> fulfillmentMethods,
        List<PickupLocationResponse> pickupLocations
) {
}
