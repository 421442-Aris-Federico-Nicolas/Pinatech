package com.computerstore.guest.service;

import com.computerstore.common.exception.InvalidRequestException;
import com.computerstore.guest.dto.GuestCustomerRequest;
import com.computerstore.guest.dto.GuestDeliveryAddressRequest;
import com.computerstore.order.domain.BuyerSnapshot;
import com.computerstore.order.domain.DeliveryAddressSnapshot;
import com.computerstore.shipping.service.ArgentineProvinceMapper;
import java.util.Locale;
import java.util.regex.Pattern;

public final class GuestCheckoutNormalizer {
    private static final Pattern NAME = Pattern.compile("^[\\p{L}][\\p{L} .'-]{0,99}$");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern PHONE = Pattern.compile("^[+0-9() .-]{6,50}$");
    private static final Pattern POSTAL_CODE = Pattern.compile("^[A-Z0-9 -]{4,12}$");

    private GuestCheckoutNormalizer() {}

    public static Customer customer(GuestCustomerRequest request) {
        String firstName = text(request.firstName(), 100, "firstName");
        String lastName = text(request.lastName(), 100, "lastName");
        if (!NAME.matcher(firstName).matches() || !NAME.matcher(lastName).matches()) {
            throw new InvalidRequestException("First and last name contain invalid characters.");
        }
        String email = email(request.email());
        String phone = text(request.phone(), 50, "phone");
        if (!PHONE.matcher(phone).matches() || phone.replaceAll("\\D", "").length() < 6) {
            throw new InvalidRequestException("A valid phone is required.");
        }
        String document = request.documentNumber() == null ? "" : request.documentNumber().replaceAll("[ .-]", "");
        if (!document.matches("^[0-9]{6,11}$")) {
            throw new InvalidRequestException("A valid Argentine document number is required.");
        }
        return new Customer(firstName, lastName, email, phone, document);
    }

    public static Address address(GuestDeliveryAddressRequest request, Customer customer) {
        if (request == null) throw new InvalidRequestException("A complete delivery address is required.");
        String country = text(request.countryCode(), 2, "countryCode").toUpperCase(Locale.ROOT);
        if (!"AR".equals(country)) throw new InvalidRequestException("Delivery is only available in Argentina.");
        var province = ArgentineProvinceMapper.province(request.province());
        String postalCode = text(request.postalCode(), 12, "postalCode").toUpperCase(Locale.ROOT);
        if (!POSTAL_CODE.matcher(postalCode).matches()) throw new InvalidRequestException("A valid postal code is required.");
        return new Address(text(request.street(), 150, "street"), text(request.streetNumber(), 30, "streetNumber"),
                optional(request.floorApartment(), 50), text(request.locality(), 120, "locality"),
                province.name(), province.code(), postalCode, optional(request.reference(), 300), country, customer);
    }

    public static String email(String supplied) {
        String value = supplied == null ? "" : supplied.trim().toLowerCase(Locale.ROOT);
        if (value.length() > 254 || !EMAIL.matcher(value).matches()) {
            throw new InvalidRequestException("A valid email is required.");
        }
        return value;
    }

    private static String text(String supplied, int max, String field) {
        String value = supplied == null ? "" : supplied.trim().replaceAll("\\s+", " ");
        if (value.isEmpty() || value.length() > max || value.chars().anyMatch(Character::isISOControl)) {
            throw new InvalidRequestException("A valid " + field + " is required.");
        }
        return value;
    }

    private static String optional(String supplied, int max) {
        if (supplied == null || supplied.isBlank()) return null;
        return text(supplied, max, "optional delivery field");
    }

    public record Customer(String firstName, String lastName, String email, String phone, String documentNumber) {
        public BuyerSnapshot snapshot() { return new BuyerSnapshot(firstName, lastName, email, phone, documentNumber); }
    }

    public record Address(String street, String streetNumber, String floorApartment, String locality,
                          String province, String provinceCode, String postalCode, String reference,
                          String countryCode, Customer customer) {
        public DeliveryAddressSnapshot snapshot() {
            return new DeliveryAddressSnapshot((customer.firstName() + " " + customer.lastName()).trim(),
                    customer.documentNumber(), customer.email(), customer.phone(), street, streetNumber,
                    floorApartment, locality, province, provinceCode, postalCode, countryCode, reference);
        }
    }
}
