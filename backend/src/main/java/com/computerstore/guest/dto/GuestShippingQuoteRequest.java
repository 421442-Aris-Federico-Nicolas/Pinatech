package com.computerstore.guest.dto;

import com.computerstore.shipping.dto.ShippingQuoteRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record GuestShippingQuoteRequest(
        @Valid @NotEmpty @Size(max = 50) List<ShippingQuoteRequest.Item> items,
        @NotNull @Valid GuestCustomerRequest customer,
        @NotNull @Valid GuestDeliveryAddressRequest deliveryAddress
) {}
