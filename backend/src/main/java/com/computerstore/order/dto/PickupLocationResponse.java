package com.computerstore.order.dto;

import java.util.List;

import com.computerstore.order.domain.PickupLocationSnapshot;

public record PickupLocationResponse(
        String code,
        String name,
        List<String> addressLines,
        String locality,
        String provinceCode,
        String postalCode,
        String instructions,
        String hours,
        String version
) {
    public static PickupLocationResponse from(PickupLocationSnapshot location) {
        return new PickupLocationResponse(
                location.getCode(), location.getName(), location.getAddressLines(), location.getLocality(),
                location.getProvinceCode(), location.getPostalCode(), location.getInstructions(), location.getHours(),
                location.version());
    }
}
