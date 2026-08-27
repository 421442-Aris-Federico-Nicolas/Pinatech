package com.computerstore.order.service;

import java.util.List;
import java.util.Optional;

import com.computerstore.common.exception.BusinessRuleException;
import com.computerstore.common.exception.InvalidRequestException;
import com.computerstore.order.config.FulfillmentProperties;
import com.computerstore.order.domain.CustomerOrder;
import com.computerstore.order.domain.FulfillmentMethod;
import com.computerstore.order.domain.PickupLocationSnapshot;
import org.springframework.stereotype.Component;

@Component
public class FulfillmentPolicy {

    private final FulfillmentProperties properties;

    public FulfillmentPolicy(FulfillmentProperties properties) {
        this.properties = properties;
    }

    public List<FulfillmentMethod> availableMethods() {
        return properties.pickupAvailable() ? List.of(FulfillmentMethod.PICKUP) : List.of();
    }

    public Optional<PickupLocationSnapshot> activePickupLocation() {
        if (!properties.pickupAvailable()) {
            return Optional.empty();
        }
        var pickup = properties.pickup();
        return Optional.of(new PickupLocationSnapshot(
                pickup.code(), pickup.name(), pickup.addressLines(), pickup.locality(), pickup.provinceCode(),
                pickup.postalCode(), pickup.instructions(), pickup.hours()));
    }

    public PickupLocationSnapshot select(
            FulfillmentMethod method,
            String pickupLocationCode,
            String pickupLocationVersion
    ) {
        if (method != FulfillmentMethod.PICKUP) {
            throw new InvalidRequestException("Only PICKUP fulfillment is currently supported.");
        }
        PickupLocationSnapshot location = activePickupLocation()
                .orElseThrow(() -> new BusinessRuleException("Pickup fulfillment is currently unavailable."));
        if (pickupLocationCode == null || !location.getCode().equals(pickupLocationCode.trim())) {
            throw new InvalidRequestException("The selected pickup location is not available.");
        }
        if (pickupLocationVersion == null || !location.version().equals(pickupLocationVersion.trim())) {
            throw new InvalidRequestException("The selected pickup location has changed.");
        }
        return location;
    }

    public void validatePayment(CustomerOrder order) {
        PickupLocationSnapshot active = activePickupLocation()
                .orElseThrow(() -> new BusinessRuleException("Pickup fulfillment is currently unavailable."));
        PickupLocationSnapshot selected = order.getPickupLocation();
        if (order.getFulfillmentMethod() != FulfillmentMethod.PICKUP
                || selected == null
                || !active.getCode().equals(selected.getCode())
                || !active.version().equals(selected.version())) {
            throw new BusinessRuleException("The order fulfillment is not eligible for payment.");
        }
    }
}
