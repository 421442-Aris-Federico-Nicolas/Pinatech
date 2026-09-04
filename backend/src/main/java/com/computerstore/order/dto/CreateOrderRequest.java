package com.computerstore.order.dto;

import java.util.List;
import java.util.UUID;

import com.computerstore.order.domain.FulfillmentMethod;
import com.computerstore.order.domain.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateOrderRequest(
        @NotEmpty @Size(max = 50) List<@Valid Item> items,
        @NotNull PaymentMethod paymentMethod,
        @NotNull FulfillmentMethod fulfillmentMethod,
        @Size(max = 100) String pickupLocationCode,
        @Size(max = 64) String pickupLocationVersion,
        UUID shippingQuoteId
) {
    public CreateOrderRequest(List<Item> items, FulfillmentMethod fulfillmentMethod,
                              String pickupLocationCode, String pickupLocationVersion) {
        this(items, PaymentMethod.MERCADO_PAGO, fulfillmentMethod, pickupLocationCode, pickupLocationVersion, null);
    }

    public CreateOrderRequest(List<Item> items, PaymentMethod paymentMethod, FulfillmentMethod fulfillmentMethod,
                              String pickupLocationCode, String pickupLocationVersion) {
        this(items, paymentMethod, fulfillmentMethod, pickupLocationCode, pickupLocationVersion, null);
    }

    @jakarta.validation.constraints.AssertTrue(message = "Fulfillment selection is invalid")
    public boolean isFulfillmentSelectionValid() {
        if (fulfillmentMethod == null) return true;
        boolean pickupCode = pickupLocationCode != null && !pickupLocationCode.isBlank();
        boolean pickupVersion = pickupLocationVersion != null && !pickupLocationVersion.isBlank();
        return fulfillmentMethod == FulfillmentMethod.PICKUP
                ? pickupCode && pickupVersion && shippingQuoteId == null
                : !pickupCode && !pickupVersion && shippingQuoteId != null;
    }

    public record Item(
            @NotNull @Positive Long variantId,
            @NotNull @Min(1) @Max(99) Integer quantity
    ) {
    }
}
