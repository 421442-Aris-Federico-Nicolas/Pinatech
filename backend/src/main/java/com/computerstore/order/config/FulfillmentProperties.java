package com.computerstore.order.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.fulfillment")
public record FulfillmentProperties(Pickup pickup) {

    public FulfillmentProperties {
        pickup = pickup == null ? new Pickup(false, null, null, List.of(), null, null, null, null, null) : pickup;
    }

    public boolean pickupAvailable() {
        return pickup.enabled()
                && valid(pickup.code(), 100)
                && valid(pickup.name(), 150)
                && !pickup.addressLines().isEmpty()
                && pickup.addressLines().stream()
                        .allMatch(line -> valid(line, 300) && !line.contains("\n") && !line.contains("\r"))
                && String.join("\n", pickup.addressLines()).length() <= 1000
                && valid(pickup.locality(), 100)
                && valid(pickup.provinceCode(), 20)
                && valid(pickup.postalCode(), 20)
                && valid(pickup.instructions(), 1000)
                && valid(pickup.hours(), 500);
    }

    public record Pickup(
            boolean enabled,
            String code,
            String name,
            List<String> addressLines,
            String locality,
            String provinceCode,
            String postalCode,
            String instructions,
            String hours
    ) {
        public Pickup {
            code = trim(code);
            name = trim(name);
            addressLines = addressLines == null
                    ? List.of()
                    : addressLines.stream().map(FulfillmentProperties::trim).toList();
            locality = trim(locality);
            provinceCode = trim(provinceCode);
            postalCode = trim(postalCode);
            instructions = trim(instructions);
            hours = trim(hours);
        }
    }

    private static boolean valid(String value, int maxLength) {
        return value != null && !value.isBlank() && value.length() <= maxLength;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
