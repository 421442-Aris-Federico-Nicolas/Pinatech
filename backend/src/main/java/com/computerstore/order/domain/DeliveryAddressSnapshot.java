package com.computerstore.order.domain;

import com.computerstore.shipping.gateway.ZipnovaGateway;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class DeliveryAddressSnapshot {
    @Column(name = "delivery_recipient_name", length = 200) private String recipientName;
    @Column(name = "delivery_document", length = 50) private String document;
    @Column(name = "delivery_email", length = 254) private String email;
    @Column(name = "delivery_phone", length = 50) private String phone;
    @Column(name = "delivery_street", length = 150) private String street;
    @Column(name = "delivery_street_number", length = 30) private String streetNumber;
    @Column(name = "delivery_floor_apartment", length = 50) private String floorApartment;
    @Column(name = "delivery_locality", length = 120) private String locality;
    @Column(name = "delivery_province", length = 100) private String province;
    @Column(name = "delivery_province_code", length = 4) private String provinceCode;
    @Column(name = "delivery_postal_code", length = 12) private String postalCode;
    @Column(name = "delivery_country_code", length = 2) private String countryCode;
    @Column(name = "delivery_reference", length = 300) private String reference;

    protected DeliveryAddressSnapshot() {}

    public DeliveryAddressSnapshot(String recipientName, String document, String email, String phone,
            String street, String streetNumber, String floorApartment, String locality, String province,
            String provinceCode, String postalCode, String countryCode, String reference) {
        this.recipientName = recipientName; this.document = document; this.email = email; this.phone = phone;
        this.street = street; this.streetNumber = streetNumber; this.floorApartment = floorApartment;
        this.locality = locality; this.province = province; this.provinceCode = provinceCode;
        this.postalCode = postalCode; this.countryCode = countryCode; this.reference = reference;
    }

    public ZipnovaGateway.Destination toDestination() {
        return new ZipnovaGateway.Destination(recipientName, document, email, phone, street, streetNumber,
                streetExtras(), locality, province, postalCode);
    }

    private String streetExtras() {
        String floor = floorApartment == null ? "" : floorApartment.trim();
        String directions = reference == null ? "" : reference.trim();
        String value = floor.isEmpty() ? directions : directions.isEmpty() ? floor : floor + " - " + directions;
        return value.isEmpty() ? null : value.substring(0, Math.min(300, value.length()));
    }

    public String getRecipientName() { return recipientName; }
    public String getDocument() { return document; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getStreet() { return street; }
    public String getStreetNumber() { return streetNumber; }
    public String getFloorApartment() { return floorApartment; }
    public String getLocality() { return locality; }
    public String getProvince() { return province; }
    public String getProvinceCode() { return provinceCode; }
    public String getPostalCode() { return postalCode; }
    public String getCountryCode() { return countryCode; }
    public String getReference() { return reference; }
}
