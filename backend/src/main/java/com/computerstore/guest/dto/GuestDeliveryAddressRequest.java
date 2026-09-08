package com.computerstore.guest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GuestDeliveryAddressRequest(
        @NotBlank @Size(max = 150) String street,
        @NotBlank @Size(max = 30) String streetNumber,
        @Size(max = 50) String floorApartment,
        @NotBlank @Size(max = 120) String locality,
        @NotBlank @Size(max = 100) String province,
        @NotBlank @Size(max = 12) String postalCode,
        @Size(max = 300) String reference,
        @NotBlank @Size(max = 2) String countryCode
) {}
