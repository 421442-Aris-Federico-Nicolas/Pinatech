package com.computerstore.order.domain;

import java.util.List;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class PickupLocationSnapshot {

    private static final String ADDRESS_SEPARATOR = "\n";

    @Column(name = "pickup_location_code", length = 100)
    private String code;

    @Column(name = "pickup_location_name", length = 150)
    private String name;

    @Column(name = "pickup_address_lines", length = 1000)
    private String serializedAddressLines;

    @Column(name = "pickup_locality", length = 100)
    private String locality;

    @Column(name = "pickup_province_code", length = 20)
    private String provinceCode;

    @Column(name = "pickup_postal_code", length = 20)
    private String postalCode;

    @Column(name = "pickup_instructions", length = 1000)
    private String instructions;

    @Column(name = "pickup_hours", length = 500)
    private String hours;

    protected PickupLocationSnapshot() {
    }

    public PickupLocationSnapshot(
            String code,
            String name,
            List<String> addressLines,
            String locality,
            String provinceCode,
            String postalCode,
            String instructions,
            String hours
    ) {
        this.code = code;
        this.name = name;
        this.serializedAddressLines = String.join(ADDRESS_SEPARATOR, addressLines);
        this.locality = locality;
        this.provinceCode = provinceCode;
        this.postalCode = postalCode;
        this.instructions = instructions;
        this.hours = hours;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public List<String> getAddressLines() {
        return serializedAddressLines == null
                ? List.of()
                : List.of(serializedAddressLines.split(ADDRESS_SEPARATOR, -1));
    }
    public String getLocality() { return locality; }
    public String getProvinceCode() { return provinceCode; }
    public String getPostalCode() { return postalCode; }
    public String getInstructions() { return instructions; }
    public String getHours() { return hours; }

    public String version() {
        StringBuilder canonical = new StringBuilder();
        append(canonical, code);
        append(canonical, name);
        getAddressLines().forEach(line -> append(canonical, line));
        append(canonical, locality);
        append(canonical, provinceCode);
        append(canonical, postalCode);
        append(canonical, instructions);
        append(canonical, hours);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    private static void append(StringBuilder target, String value) {
        String safeValue = value == null ? "" : value;
        target.append(safeValue.length()).append(':').append(safeValue);
    }
}
