package com.computerstore.order.dto;

import java.util.List;

import com.computerstore.order.domain.FulfillmentMethod;
import com.computerstore.order.domain.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateOrderRequest(
        @NotEmpty @Size(max = 50) List<@Valid Item> items,
        @NotNull PaymentMethod paymentMethod,
        @NotNull FulfillmentMethod fulfillmentMethod,
        @NotBlank @Size(max = 100) String pickupLocationCode,
        @NotBlank @Size(max = 64) String pickupLocationVersion
) {
    public CreateOrderRequest(List<Item> items, FulfillmentMethod fulfillmentMethod,
                              String pickupLocationCode, String pickupLocationVersion) {
        this(items, PaymentMethod.MERCADO_PAGO, fulfillmentMethod, pickupLocationCode, pickupLocationVersion);
    }

    public record Item(
            @NotNull @Positive Long variantId,
            @NotNull @Min(1) @Max(99) Integer quantity
    ) {
    }
}
