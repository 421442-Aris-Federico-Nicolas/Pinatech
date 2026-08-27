package com.computerstore.profile.dto;

public record AddressResponse(
        String street,
        String number,
        String floorApartment,
        String locality,
        String provinceCode,
        String postalCode,
        String countryCode,
        String reference
) {
}
