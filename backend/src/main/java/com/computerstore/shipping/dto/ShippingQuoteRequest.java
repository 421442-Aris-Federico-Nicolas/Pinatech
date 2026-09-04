package com.computerstore.shipping.dto;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record ShippingQuoteRequest(@NotEmpty @Size(max = 50) List<@Valid Item> items) {
    public record Item(@NotNull @Positive Long variantId, @NotNull @Min(1) @Max(99) Integer quantity) {}
}
