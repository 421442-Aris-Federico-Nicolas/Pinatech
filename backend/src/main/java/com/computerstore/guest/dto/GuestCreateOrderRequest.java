package com.computerstore.guest.dto;

import com.computerstore.order.domain.FulfillmentMethod;
import com.computerstore.order.domain.PaymentMethod;
import com.computerstore.order.dto.CreateOrderRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

public record GuestCreateOrderRequest(
        @Valid @NotEmpty @Size(max = 50) List<CreateOrderRequest.Item> items,
        @NotNull PaymentMethod paymentMethod,
        @NotNull FulfillmentMethod fulfillmentMethod,
        @Size(max = 100) String pickupLocationCode,
        @Size(max = 64) String pickupLocationVersion,
        UUID shippingQuoteId,
        @NotNull @Valid GuestCustomerRequest customer,
        @Valid GuestDeliveryAddressRequest deliveryAddress
) {
    @AssertTrue(message = "Fulfillment selection is invalid")
    public boolean isFulfillmentSelectionValid() {
        if (fulfillmentMethod == null) return true;
        boolean pickupCode = pickupLocationCode != null && !pickupLocationCode.isBlank();
        boolean pickupVersion = pickupLocationVersion != null && !pickupLocationVersion.isBlank();
        return fulfillmentMethod == FulfillmentMethod.PICKUP
                ? pickupCode && pickupVersion && shippingQuoteId == null && deliveryAddress == null
                : !pickupCode && !pickupVersion && shippingQuoteId != null && deliveryAddress != null;
    }
}
