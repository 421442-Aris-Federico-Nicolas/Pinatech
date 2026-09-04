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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import com.computerstore.shipping.config.ZipnovaProperties;

@Component
public class FulfillmentPolicy {

    private final FulfillmentProperties properties;
    private final ZipnovaProperties shipping;

    public FulfillmentPolicy(FulfillmentProperties properties) {
        this.properties = properties;
        this.shipping = null;
    }

    @Autowired
    public FulfillmentPolicy(FulfillmentProperties properties, ObjectProvider<ZipnovaProperties> shipping) {
        this.properties = properties;
        this.shipping = shipping.getIfAvailable();
    }

    public List<FulfillmentMethod> availableMethods() {
        java.util.ArrayList<FulfillmentMethod> methods = new java.util.ArrayList<>();
        if (properties.pickupAvailable()) methods.add(FulfillmentMethod.PICKUP);
        if (shipping != null && shipping.available()) methods.add(FulfillmentMethod.DELIVERY);
        return List.copyOf(methods);
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
        if (method != FulfillmentMethod.PICKUP) throw new InvalidRequestException("Pickup selection is invalid.");
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
        if (order.getFulfillmentMethod() == FulfillmentMethod.DELIVERY) {
            if (shipping == null || !shipping.available() || !"ZIPNOVA".equals(order.getDeliveryMethod()) || order.getDeliveryAddress() == null
                    || order.getShippingQuote() == null || order.getShippingCost() == null) {
                throw new BusinessRuleException("The order delivery snapshot is not eligible for payment.");
            }
            return;
        }
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
