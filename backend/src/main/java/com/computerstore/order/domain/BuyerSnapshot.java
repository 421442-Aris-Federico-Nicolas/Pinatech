package com.computerstore.order.domain;

import com.computerstore.user.domain.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class BuyerSnapshot {
    @Column(name = "buyer_first_name", nullable = false, updatable = false, length = 100)
    private String firstName;
    @Column(name = "buyer_last_name", nullable = false, updatable = false, length = 100)
    private String lastName;
    @Column(name = "buyer_email", nullable = false, updatable = false, length = 254)
    private String email;
    @Column(name = "buyer_phone", nullable = false, updatable = false, length = 50)
    private String phone;
    @Column(name = "buyer_document_number", nullable = false, updatable = false, length = 50)
    private String documentNumber;

    protected BuyerSnapshot() {}

    public BuyerSnapshot(String firstName, String lastName, String email, String phone, String documentNumber) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.documentNumber = documentNumber;
    }

    public static BuyerSnapshot from(UserAccount user) {
        return new BuyerSnapshot(user.getFirstName(), user.getLastName(), user.getEmail(),
                user.getPhone() == null || user.getPhone().isBlank() ? "N/A" : user.getPhone(),
                user.getDocumentNumber() == null || user.getDocumentNumber().isBlank() ? "N/A" : user.getDocumentNumber());
    }

    public String fullName() { return (firstName + " " + lastName).trim(); }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getDocumentNumber() { return documentNumber; }
}
