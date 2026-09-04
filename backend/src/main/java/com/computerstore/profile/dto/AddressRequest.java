package com.computerstore.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @NotBlank @Size(max = 150) String street,
        @NotBlank @Size(max = 30) String number,
        @Size(max = 50) String floorApartment,
        @NotBlank @Size(max = 120) String locality,
        @NotBlank @Pattern(regexp = "[A-Za-z]{1,4}") String provinceCode,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9 -]{4,12}") String postalCode,
        @Pattern(regexp = "[A-Za-z]{2}") String countryCode,
        @Size(max = 300) String reference
) {
}
