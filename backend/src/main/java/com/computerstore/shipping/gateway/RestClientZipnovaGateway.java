package com.computerstore.shipping.gateway;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.computerstore.shipping.config.ZipnovaProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class RestClientZipnovaGateway implements ZipnovaGateway {
    private static final int MAX_ITEMS = 1000;
    private static final int MAX_PDF_BYTES = 20 * 1024 * 1024;
    private final RestClient client;
    private final ZipnovaProperties properties;

    public RestClientZipnovaGateway(@Qualifier("zipnovaRestClient") RestClient client,
                                    ZipnovaProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public List<QuoteOption> quote(QuoteCommand command) {
        properties.requireAvailable();
        validateItems(command.items());
        try {
            var request = new QuoteProviderRequest(properties.accountId(), properties.originId(), properties.source(),
                    command.declaredValue(), new QuoteDestination(command.destination().city(), command.destination().state(),
                    command.destination().zipcode(), command.destination().street(), command.destination().streetNumber()),
                    providerItems(command.items()), properties.packagingMode());
            JsonNode root = client.post().uri("/shipments/quote").contentType(MediaType.APPLICATION_JSON)
                    .body(request).retrieve().body(JsonNode.class);
            JsonNode results = required(root, "all_results");
            if (!results.isArray()) throw invalidResponse("all_results");
            List<QuoteOption> options = new ArrayList<>();
            for (JsonNode result : results) {
                if (!requiredBoolean(result, "selectable")) continue;
                JsonNode service = required(result, "service_type");
                String serviceCode = requiredText(service, "code");
                if ("pickup_point".equalsIgnoreCase(serviceCode)) continue;
                JsonNode carrier = required(result, "carrier");
                JsonNode amounts = required(result, "amounts");
                List<String> tags = new ArrayList<>();
                JsonNode tagsNode = result.path("tags");
                if (!tagsNode.isMissingNode() && !tagsNode.isNull()) {
                    if (!tagsNode.isArray()) throw invalidResponse("tags");
                    tagsNode.forEach(tag -> tags.add(tag.asText()));
                }
                options.add(new QuoteOption(requiredLong(carrier, "id"), requiredText(carrier, "name"),
                        serviceCode, requiredText(service, "name"), requiredText(result, "logistic_type"),
                        requiredDecimal(amounts, "price_incl_tax"), optionalInstant(result.path("delivery_time"),
                        "estimated_delivery"), List.copyOf(tags)));
            }
            return List.copyOf(options);
        } catch (RestClientResponseException exception) {
            throw responseFailure("quote", exception, false);
        } catch (ResourceAccessException exception) {
            throw unavailable("quote", true, exception);
        }
    }

    @Override
    public ProviderShipment createShipment(CreateShipmentCommand command) {
        properties.requireAvailable();
        validateItems(command.items());
        var destination = new ProviderDestination(command.destination().name(), command.destination().document(),
                command.destination().email(), command.destination().phone(), command.destination().street(),
                command.destination().streetNumber(), command.destination().streetExtras(), command.destination().city(),
                command.destination().state(), command.destination().zipcode());
        var body = new CreateProviderRequest(properties.accountId(), command.externalId(), command.serviceCode(),
                command.logisticType(), command.carrierId(), properties.originId(), command.declaredValue(),
                properties.source(), properties.packagingMode(), 0, destination, providerItems(command.items()));
        try {
            JsonNode response = client.post().uri("/shipments").contentType(MediaType.APPLICATION_JSON)
                    .body(body).retrieve().body(JsonNode.class);
            try {
                return parseShipment(response);
            } catch (ShippingProviderException invalidResponse) {
                throw new ShippingProviderException(invalidResponse.getMessage(), null, true, true,
                        invalidResponse);
            }
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            throw responseFailure("creation", exception, status == 409 || status >= 500);
        } catch (ResourceAccessException exception) {
            throw unavailable("creation", true, exception);
        }
    }

    @Override public ProviderShipment getShipment(long id) {
        return shipmentGet("/shipments/{id}", id);
    }

    @Override
    public ProviderShipment findByExternalId(String externalId) {
        properties.requireAvailable();
        try {
            JsonNode root = client.get().uri(uri -> uri.path("/shipments").queryParam("external_id", externalId)
                    .queryParam("account_id", properties.accountId()).build()).retrieve().body(JsonNode.class);
            JsonNode data = required(root, "data");
            if (!data.isArray()) throw invalidResponse("data");
            if (data.isEmpty()) return null;
            long id = requiredLong(data.get(0), "id");
            return getShipment(id);
        } catch (RestClientResponseException exception) {
            throw responseFailure("external ID lookup", exception, false);
        } catch (ResourceAccessException exception) {
            throw unavailable("external ID lookup", false, exception);
        }
    }

    @Override
    public List<TrackingEvent> tracking(long id) {
        properties.requireAvailable();
        try {
            JsonNode root = client.get().uri(uri -> uri.path("/shipments/{id}/tracking")
                    .queryParam("sort", "oldest").build(id)).retrieve().body(JsonNode.class);
            if (root == null || !root.isArray()) throw invalidResponse("tracking");
            List<TrackingEvent> result = new ArrayList<>();
            for (JsonNode event : root) {
                JsonNode status = required(event, "status");
                result.add(new TrackingEvent(requiredText(status, "code"), optionalText(status, "substatus_code"),
                        requiredInstant(event, "occurred_at")));
            }
            return List.copyOf(result);
        } catch (RestClientResponseException exception) {
            throw responseFailure("tracking", exception, false);
        } catch (ResourceAccessException exception) {
            throw unavailable("tracking", false, exception);
        }
    }

    @Override public byte[] label(long id) { return pdf(id, "label"); }
    @Override public byte[] document(long id) { return pdf(id, "document"); }

    @Override
    public String cancel(long id) {
        properties.requireAvailable();
        try {
            JsonNode root = client.post().uri("/shipments/{id}/cancel", id).retrieve().body(JsonNode.class);
            if (!requiredBoolean(root, "success")) throw invalidResponse("success");
            return requiredText(root, "result");
        } catch (RestClientResponseException exception) {
            throw responseFailure("cancellation", exception, false);
        } catch (ResourceAccessException exception) {
            throw unavailable("cancellation", false, exception);
        }
    }

    private ProviderShipment shipmentGet(String path, long id) {
        properties.requireAvailable();
        if (id <= 0) throw new IllegalArgumentException("Provider shipment ID must be positive.");
        try {
            return parseShipment(client.get().uri(path, id).retrieve().body(JsonNode.class));
        } catch (RestClientResponseException exception) {
            throw responseFailure("shipment lookup", exception, false);
        } catch (ResourceAccessException exception) {
            throw unavailable("shipment lookup", false, exception);
        }
    }

    private byte[] pdf(long id, String what) {
        properties.requireAvailable();
        try {
            ResponseEntity<byte[]> response = client.get().uri("/shipments/{id}/{what}.pdf", id, what)
                    .accept(MediaType.APPLICATION_PDF).retrieve().toEntity(byte[].class);
            byte[] body = response.getBody();
            MediaType type = response.getHeaders().getContentType();
            if (body == null || body.length < 5 || body.length > MAX_PDF_BYTES
                    || body[0] != '%' || body[1] != 'P' || body[2] != 'D' || body[3] != 'F'
                    || (type != null && !MediaType.APPLICATION_PDF.isCompatibleWith(type))) {
                throw invalidResponse(what + " PDF");
            }
            return body;
        } catch (RestClientResponseException exception) {
            throw responseFailure(what + " download", exception, false);
        } catch (ResourceAccessException exception) {
            throw unavailable(what + " download", false, exception);
        }
    }

    private List<ProviderItem> providerItems(List<Item> items) { return items.stream().map(item -> new ProviderItem(
            item.weight(), item.height(), item.width(), item.length(), item.classificationId(), item.description(),
            item.mustKeepVertical())).toList(); }

    private ProviderShipment parseShipment(JsonNode root) {
        String tracking = httpsUrl(optionalText(root, "tracking"));
        String externalTracking = httpsUrl(optionalText(root, "tracking_external"));
        JsonNode carrier = required(root, "carrier");
        Instant updated = optionalInstant(root, "last_status_at");
        if (updated == null) updated = requiredInstant(root, "created_at");
        return new ProviderShipment(requiredLong(root, "id"), requiredText(root, "external_id"),
                requiredText(root, "status"), optionalText(root, "substatus_code"),
                optionalText(root, "carrier_tracking_id"), externalTracking == null ? tracking : externalTracking,
                optionalInstant(root.path("delivery_time"), "estimated_delivery"), updated,
                requiredText(carrier, "name"));
    }

    private void validateItems(List<Item> items) {
        if (items == null || items.isEmpty() || items.size() > MAX_ITEMS) {
            throw new IllegalArgumentException("Zipnova requires between 1 and 1000 expanded items.");
        }
        for (Item item : items) {
            if (item.weight() < 10 || item.weight() > 10_000_000 || item.height() < 1 || item.height() > 5000
                    || item.width() < 1 || item.width() > 5000 || item.length() < 1 || item.length() > 5000
                    || item.classificationId() == null || item.classificationId().isBlank()) {
                throw new IllegalArgumentException("A shipping item has invalid authoritative dimensions.");
            }
        }
    }

    private ShippingProviderException responseFailure(String operation, RestClientResponseException error,
                                                      boolean ambiguous) {
        int status = error.getStatusCode().value();
        Duration retry = status == 429 ? retryAfter(error.getResponseHeaders()) : null;
        return new ShippingProviderException("Zipnova " + operation + " returned HTTP " + status + ".",
                retry, ambiguous, status == 409 || status == 429 || status >= 500, null);
    }

    private ShippingProviderException unavailable(String operation, boolean ambiguous, RuntimeException cause) {
        return new ShippingProviderException("Zipnova " + operation + " could not reach the provider.",
                null, ambiguous, true, cause);
    }

    private Duration retryAfter(HttpHeaders headers) {
        String value = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null) return Duration.ofMinutes(1);
        try { return Duration.ofSeconds(Math.max(1, Long.parseLong(value.trim()))); }
        catch (NumberFormatException ignored) {
            try { return Duration.between(Instant.now(), ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant())
                    .isNegative() ? Duration.ofSeconds(1) : Duration.between(Instant.now(), ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()); }
            catch (RuntimeException invalid) { return Duration.ofMinutes(1); }
        }
    }

    private JsonNode required(JsonNode node, String field) {
        if (node == null || node.get(field) == null || node.get(field).isNull()) throw invalidResponse(field);
        return node.get(field);
    }
    private String requiredText(JsonNode node, String field) {
        String value = optionalText(node, field);
        if (value == null || value.isBlank()) throw invalidResponse(field);
        return value;
    }
    private String optionalText(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
    private long requiredLong(JsonNode node, String field) {
        JsonNode value = required(node, field);
        if (!value.canConvertToLong() || value.longValue() <= 0) throw invalidResponse(field);
        return value.longValue();
    }
    private boolean requiredBoolean(JsonNode node, String field) {
        JsonNode value = required(node, field);
        if (!value.isBoolean()) throw invalidResponse(field);
        return value.booleanValue();
    }
    private java.math.BigDecimal requiredDecimal(JsonNode node, String field) {
        JsonNode value = required(node, field);
        if (!value.isNumber() || value.decimalValue().signum() < 0) throw invalidResponse(field);
        return value.decimalValue().setScale(2, java.math.RoundingMode.HALF_UP);
    }
    private Instant requiredInstant(JsonNode node, String field) {
        Instant result = optionalInstant(node, field);
        if (result == null) throw invalidResponse(field);
        return result;
    }
    private Instant optionalInstant(JsonNode node, String field) {
        String value = optionalText(node, field);
        if (value == null || value.isBlank()) return null;
        try { return OffsetDateTime.parse(value).toInstant(); }
        catch (RuntimeException error) { try { return Instant.parse(value); } catch (RuntimeException ignored) { throw invalidResponse(field); } }
    }
    private String httpsUrl(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            URI uri = URI.create(value);
            return uri.isAbsolute() && "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && uri.getRawUserInfo() == null ? uri.toString() : null;
        } catch (IllegalArgumentException ignored) { return null; }
    }
    private ShippingProviderException invalidResponse(String field) {
        return new ShippingProviderException("Zipnova returned an invalid " + field + " field.", null, false,
                false, null);
    }
    private record QuoteProviderRequest(@JsonProperty("account_id") Long accountId,
            @JsonProperty("origin_id") Long originId, String source,
            @JsonProperty("declared_value") java.math.BigDecimal declaredValue, QuoteDestination destination,
            List<ProviderItem> items, @JsonProperty("type_packaging") String typePackaging) {}
    private record QuoteDestination(String city, String state, String zipcode, String street,
            @JsonProperty("street_number") String streetNumber) {}
    private record CreateProviderRequest(@JsonProperty("account_id") Long accountId,
            @JsonProperty("external_id") String externalId, @JsonProperty("service_type") String serviceType,
            @JsonProperty("logistic_type") String logisticType, @JsonProperty("carrier_id") long carrierId,
            @JsonProperty("origin_id") Long originId, @JsonProperty("declared_value") java.math.BigDecimal declaredValue,
            String source, @JsonProperty("type_packaging") String typePackaging,
            @JsonProperty("process_immediately") int processImmediately, ProviderDestination destination,
            List<ProviderItem> items) {}
    private record ProviderDestination(String name, String document, String email, String phone, String street,
            @JsonProperty("street_number") String streetNumber, @JsonProperty("street_extras") String streetExtras,
            String city, String state, String zipcode) {}
    private record ProviderItem(int weight, int height, int width, int length,
            @JsonProperty("classification_id") String classificationId, String description,
            @JsonProperty("must_keep_vertical") boolean mustKeepVertical) {}
}
