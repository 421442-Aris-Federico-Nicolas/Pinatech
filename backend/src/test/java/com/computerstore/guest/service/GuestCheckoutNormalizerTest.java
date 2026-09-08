package com.computerstore.guest.service;

import static org.junit.jupiter.api.Assertions.*;

import com.computerstore.common.exception.InvalidRequestException;
import com.computerstore.guest.dto.*;
import org.junit.jupiter.api.Test;

class GuestCheckoutNormalizerTest {
    @Test
    void normalizesRequiredBuyerAndArgentineDeliveryData() {
        var customer = GuestCheckoutNormalizer.customer(new GuestCustomerRequest(
                " Ada  Maria ", " Lovelace ", " ADA@Example.COM ", "+54 351 555 0101", "12.345.678"));
        var address = GuestCheckoutNormalizer.address(new GuestDeliveryAddressRequest(
                " San  Martin ", " 123 ", " ", " Cordoba ", "AR-X", " x5000 ", " ", "ar"), customer);

        assertEquals("Ada Maria", customer.firstName());
        assertEquals("ada@example.com", customer.email());
        assertEquals("12345678", customer.documentNumber());
        assertEquals("X", address.provinceCode());
        assertEquals("X5000", address.postalCode());
        assertNull(address.floorApartment());
    }

    @Test
    void rejectsNonArgentineAndIncompleteGuestData() {
        var customer = new GuestCustomerRequest("Ada", "Lovelace", "ada@example.com", "123", "12345678");
        assertThrows(InvalidRequestException.class, () -> GuestCheckoutNormalizer.customer(customer));
        var valid = GuestCheckoutNormalizer.customer(new GuestCustomerRequest(
                "Ada", "Lovelace", "ada@example.com", "3515550101", "12345678"));
        assertThrows(InvalidRequestException.class, () -> GuestCheckoutNormalizer.address(
                new GuestDeliveryAddressRequest("Street", "1", null, "City", "X", "5000", null, "UY"), valid));
    }
}
