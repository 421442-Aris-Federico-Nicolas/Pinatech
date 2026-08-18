package com.computerstore.payment.gateway;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.computerstore.payment.config.MercadoPagoEnvironment;
import com.computerstore.payment.config.MercadoPagoProperties;
import com.computerstore.payment.exception.PaymentProviderException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class RestClientMercadoPagoGateway implements MercadoPagoGateway {

    private final RestClient client;
    private final MercadoPagoProperties properties;

    public RestClientMercadoPagoGateway(
            @Qualifier("mercadoPagoRestClient") RestClient client,
            MercadoPagoProperties properties
    ) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public PaymentPreference createPreference(PaymentPreferenceRequest request) {
        properties.requireEnabled();
        String resultPath = "/checkout/result?orderId=" + request.orderId();
        List<Map<String, Object>> items = request.items().stream()
                .map(item -> Map.<String, Object>of(
                        "id", item.id(),
                        "title", item.title(),
                        "quantity", item.quantity(),
                        "currency_id", request.currency(),
                        "unit_price", item.unitPrice()))
                .toList();
        Map<String, Object> body = Map.ofEntries(
                Map.entry("items", items),
                Map.entry("external_reference", request.attemptId().toString()),
                Map.entry("notification_url", properties.publicUrl("/api/payments/webhooks/mercado-pago")),
                Map.entry("back_urls", Map.of(
                        "success", properties.publicUrl(resultPath),
                        "pending", properties.publicUrl(resultPath),
                        "failure", properties.publicUrl(resultPath))),
                Map.entry("auto_return", "approved"),
                Map.entry("binary_mode", true),
                Map.entry("expires", true),
                Map.entry("expiration_date_to", request.expiresAt().toString()),
                Map.entry("payment_methods", Map.of("excluded_payment_types", List.of(
                        Map.of("id", "ticket"),
                        Map.of("id", "atm"),
                        Map.of("id", "bank_transfer")))));
        try {
            JsonNode response = client.post()
                    .uri("/checkout/preferences")
                    .header("X-Idempotency-Key", request.attemptId().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null) {
                throw new PaymentProviderException("Mercado Pago returned an empty preference response.");
            }
            String preferenceId = requiredText(response, "id");
            String checkoutField = properties.environment() == MercadoPagoEnvironment.SANDBOX
                    ? "sandbox_init_point" : "init_point";
            return new PaymentPreference(preferenceId, requiredText(response, checkoutField));
        } catch (RestClientResponseException | ResourceAccessException exception) {
            throw new PaymentProviderException("Mercado Pago preference request failed.", exception);
        }
    }

    @Override
    public ProviderPayment getPayment(String paymentId) {
        properties.requireEnabled();
        if (paymentId == null || !paymentId.matches("[0-9]{1,100}")) {
            throw new PaymentProviderException("Invalid Mercado Pago payment ID.");
        }
        try {
            JsonNode response = client.get()
                    .uri("/v1/payments/{paymentId}", paymentId)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null) {
                throw new PaymentProviderException("Mercado Pago returned an empty payment response.");
            }
            String preferenceId = text(response, "preference_id");
            if (preferenceId == null || preferenceId.isBlank()) {
                preferenceId = preferenceIdFromMerchantOrder(response);
            }
            return new ProviderPayment(
                    requiredText(response, "id"),
                    text(response, "external_reference"),
                    preferenceId,
                    text(response, "collector_id"),
                    response.path("transaction_amount").decimalValue(),
                    text(response, "currency_id"),
                    requiredText(response, "status"),
                    text(response, "status_detail"),
                    instant(response, "date_approved"),
                    instant(response, "date_last_updated"),
                    sha256(response.toString()));
        } catch (RestClientResponseException | ResourceAccessException exception) {
            throw new PaymentProviderException("Mercado Pago payment lookup failed.", exception);
        }
    }

    @Override
    public String refund(String paymentId, UUID idempotencyKey) {
        properties.requireEnabled();
        try {
            JsonNode response = client.post()
                    .uri("/v1/payments/{paymentId}/refunds", paymentId)
                    .header("X-Idempotency-Key", idempotencyKey.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of())
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null) {
                throw new PaymentProviderException("Mercado Pago returned an empty refund response.");
            }
            return requiredText(response, "id");
        } catch (RestClientResponseException | ResourceAccessException exception) {
            throw new PaymentProviderException("Mercado Pago refund request failed.", exception);
        }
    }

    private String requiredText(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null || value.isBlank()) {
            throw new PaymentProviderException("Mercado Pago response is missing " + field + ".");
        }
        return value;
    }

    private String preferenceIdFromMerchantOrder(JsonNode payment) {
        String merchantOrderId = text(payment.path("order"), "id");
        if (merchantOrderId == null || merchantOrderId.isBlank()) {
            throw new PaymentProviderException("Mercado Pago payment is missing its preference reference.");
        }
        JsonNode merchantOrder = client.get()
                .uri("/merchant_orders/{merchantOrderId}", merchantOrderId)
                .retrieve()
                .body(JsonNode.class);
        if (merchantOrder == null) {
            throw new PaymentProviderException("Mercado Pago returned an empty merchant order response.");
        }
        return requiredText(merchantOrder, "preference_id");
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private Instant instant(JsonNode node, String field) {
        String value = text(node, field);
        return value == null || value.isBlank() ? null : OffsetDateTime.parse(value).toInstant();
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }
}
