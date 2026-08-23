package com.computerstore.payment.gateway;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.LinkedHashSet;

import com.computerstore.payment.config.MercadoPagoEnvironment;
import com.computerstore.payment.config.MercadoPagoProperties;
import com.computerstore.payment.exception.PaymentNotFoundException;
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
                Map.entry("notification_url", properties.webhookUrl("/api/payments/webhooks/mercado-pago")),
                Map.entry("back_urls", Map.of(
                        "success", properties.storefrontUrl(resultPath),
                        "pending", properties.storefrontUrl(resultPath),
                        "failure", properties.storefrontUrl(resultPath))),
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
            String status = requiredText(response, "status");
            Instant approvedAt = instant(response, "date_approved");
            if ("approved".equalsIgnoreCase(status) && approvedAt == null) {
                throw new PaymentProviderException("Mercado Pago approved payment is missing date_approved.");
            }
            BigDecimal amount = requiredDecimal(response, "transaction_amount");
            return new ProviderPayment(
                    requiredText(response, "id"),
                    requiredText(response, "external_reference"),
                    preferenceId,
                    requiredText(response, "collector_id"),
                    amount,
                    requiredText(response, "currency_id"),
                    status,
                    text(response, "status_detail"),
                    approvedAt,
                    requiredInstant(response, "date_last_updated"),
                    requiredBoolean(response, "live_mode"),
                    requiredText(response, "operation_type"),
                    refundedAmount(response, status, amount),
                    sha256(response.toString()));
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (status == 404) {
                throw new PaymentNotFoundException("Mercado Pago payment lookup returned HTTP 404.", exception);
            }
            throw new PaymentProviderException(
                    "Mercado Pago payment lookup returned HTTP " + status + ".", exception);
        } catch (ResourceAccessException exception) {
            throw new PaymentProviderException("Mercado Pago payment lookup could not reach the provider.", exception);
        }
    }

    @Override
    public List<String> findPaymentIdsByPreference(String preferenceId) {
        properties.requireEnabled();
        try {
            JsonNode response = client.get()
                    .uri(uri -> uri.path("/merchant_orders/search")
                            .queryParam("preference_id", preferenceId).build())
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null) {
                throw new PaymentProviderException("Mercado Pago returned an empty merchant order search response.");
            }
            LinkedHashSet<String> ids = new LinkedHashSet<>();
            response.path("elements").forEach(order -> order.path("payments").forEach(payment -> {
                String id = text(payment, "id");
                if (id != null && id.matches("[0-9]{1,100}")) ids.add(id);
            }));
            return List.copyOf(ids);
        } catch (RestClientResponseException | ResourceAccessException exception) {
            throw new PaymentProviderException("Mercado Pago merchant order search failed.", exception);
        }
    }

    @Override
    public RefundResult refund(String paymentId, UUID idempotencyKey) {
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
            return refundResult(response);
        } catch (RestClientResponseException | ResourceAccessException exception) {
            throw new PaymentProviderException("Mercado Pago refund request failed.", exception);
        }
    }

    @Override
    public RefundResult getRefund(String paymentId, String refundId) {
        properties.requireEnabled();
        try {
            JsonNode response = client.get()
                    .uri("/v1/payments/{paymentId}/refunds/{refundId}", paymentId, refundId)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null) {
                throw new PaymentProviderException("Mercado Pago returned an empty refund lookup response.");
            }
            return refundResult(response);
        } catch (RestClientResponseException | ResourceAccessException exception) {
            throw new PaymentProviderException("Mercado Pago refund lookup failed.", exception);
        }
    }

    private RefundResult refundResult(JsonNode response) {
        String status = requiredText(response, "status");
        if (!List.of("pending", "approved", "rejected").contains(status.toLowerCase())) {
            throw new PaymentProviderException("Mercado Pago returned an unsupported refund status.");
        }
        return new RefundResult(requiredText(response, "id"), status, requiredDecimal(response, "amount"));
    }

    private boolean requiredBoolean(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isBoolean()) {
            throw new PaymentProviderException("Mercado Pago response is missing " + field + ".");
        }
        return value.booleanValue();
    }

    private BigDecimal requiredDecimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw new PaymentProviderException("Mercado Pago response is missing " + field + ".");
        }
        if (!value.isNumber()) {
            throw new PaymentProviderException("Mercado Pago response has an invalid " + field + ".");
        }
        return value.decimalValue();
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
        if (value == null || value.isBlank()) return null;
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException exception) {
            throw new PaymentProviderException("Mercado Pago response has an invalid " + field + ".", exception);
        }
    }

    private Instant requiredInstant(JsonNode node, String field) {
        Instant value = instant(node, field);
        if (value == null) {
            throw new PaymentProviderException("Mercado Pago response is missing " + field + ".");
        }
        return value;
    }

    private BigDecimal refundedAmount(JsonNode payment, String status, BigDecimal paymentAmount) {
        BigDecimal refunded = optionalDecimal(payment, "transaction_amount_refunded");
        if (refunded == null) {
            refunded = optionalDecimal(payment, "amount_refunded");
        }
        if (refunded == null) {
            JsonNode refunds = payment.get("refunds");
            if (refunds != null && !refunds.isNull()) {
                if (!refunds.isArray()) {
                    throw new PaymentProviderException("Mercado Pago response has an invalid refunds field.");
                }
                refunded = BigDecimal.ZERO;
                for (JsonNode refund : refunds) {
                    refunded = refunded.add(requiredDecimal(refund, "amount"));
                }
            } else {
                refunded = BigDecimal.ZERO;
            }
        }

        if ("refunded".equalsIgnoreCase(status) && refunded.compareTo(paymentAmount) != 0) {
            throw new PaymentProviderException(
                    "Mercado Pago refunded payment does not report the full refunded amount.");
        }
        return refunded;
    }

    private BigDecimal optionalDecimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isNumber()) {
            throw new PaymentProviderException("Mercado Pago response has an invalid " + field + ".");
        }
        return value.decimalValue();
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
