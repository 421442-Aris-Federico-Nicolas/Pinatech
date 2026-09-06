package com.computerstore.shipping.dto;

import com.computerstore.order.domain.OrderCancellationReason;
import com.computerstore.shipping.domain.ShipmentCancellationScope;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CancelShipmentRequest(
        @NotNull ShipmentCancellationScope scope,
        OrderCancellationReason reasonCode,
        @Size(max = 500) String internalDetail
) {
}
