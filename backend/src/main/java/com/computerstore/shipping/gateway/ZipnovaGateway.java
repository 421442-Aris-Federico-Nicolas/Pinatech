package com.computerstore.shipping.gateway;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface ZipnovaGateway {
    List<QuoteOption> quote(QuoteCommand command);
    ProviderShipment createShipment(CreateShipmentCommand command);
    ProviderShipment getShipment(long providerShipmentId);
    ProviderShipment findByExternalId(String externalId);
    List<TrackingEvent> tracking(long providerShipmentId);
    byte[] label(long providerShipmentId);
    byte[] document(long providerShipmentId);
    String cancel(long providerShipmentId);

    record Item(int weight, int height, int width, int length, String classificationId,
                String description, boolean mustKeepVertical) {}
    record Destination(String name, String document, String email, String phone, String street,
                       String streetNumber, String streetExtras, String city, String state, String zipcode) {}
    record QuoteCommand(Destination destination, BigDecimal declaredValue, List<Item> items) {}
    record QuoteOption(long carrierId, String carrierName, String serviceCode, String serviceName,
                       String logisticType, BigDecimal priceInclTax, Instant estimatedDelivery, List<String> tags) {}
    record CreateShipmentCommand(String externalId, Destination destination, BigDecimal declaredValue,
                                 String serviceCode, String logisticType, long carrierId, List<Item> items) {}
    record ProviderShipment(long id, String externalId, String status, String substatus,
                            String carrierTrackingId, String trackingUrl, Instant estimatedDelivery,
                            Instant updatedAt, String carrierName) {}
    record TrackingEvent(String status, String substatus, Instant occurredAt) {}
}
