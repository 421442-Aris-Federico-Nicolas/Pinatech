package com.computerstore.user.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_addresses")
public class UserAddress {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private UserAccount user;

    @Column(nullable = false, length = 150)
    private String street;

    @Column(name = "street_number", nullable = false, length = 30)
    private String streetNumber;

    @Column(name = "floor_apartment", length = 50)
    private String floorApartment;

    @Column(nullable = false, length = 120)
    private String locality;

    @Column(name = "province_code", nullable = false, length = 3)
    private String provinceCode;

    @Column(name = "postal_code", nullable = false, length = 12)
    private String postalCode;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(length = 300)
    private String reference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserAddress() {
    }

    public UserAddress(UserAccount user) {
        this.user = user;
    }

    public void update(String street, String streetNumber, String floorApartment, String locality,
                       String provinceCode, String postalCode, String countryCode, String reference) {
        this.street = street;
        this.streetNumber = streetNumber;
        this.floorApartment = floorApartment;
        this.locality = locality;
        this.provinceCode = provinceCode;
        this.postalCode = postalCode;
        this.countryCode = countryCode;
        this.reference = reference;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public String getStreet() { return street; }
    public String getStreetNumber() { return streetNumber; }
    public String getFloorApartment() { return floorApartment; }
    public String getLocality() { return locality; }
    public String getProvinceCode() { return provinceCode; }
    public String getPostalCode() { return postalCode; }
    public String getCountryCode() { return countryCode; }
    public String getReference() { return reference; }
}
