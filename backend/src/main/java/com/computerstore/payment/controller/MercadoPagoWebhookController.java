package com.computerstore.payment.controller;

import com.computerstore.common.exception.InvalidRequestException;
import com.computerstore.payment.service.MercadoPagoSignatureValidator;
import com.computerstore.payment.service.PaymentWebhookService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments/webhooks")
public class MercadoPagoWebhookController {

    private final ObjectMapper objectMapper;
    private final MercadoPagoSignatureValidator signatures;
    private final PaymentWebhookService webhooks;

    public MercadoPagoWebhookController(
            ObjectMapper objectMapper,
            MercadoPagoSignatureValidator signatures,
            PaymentWebhookService webhooks
    ) {
        this.objectMapper = objectMapper;
        this.signatures = signatures;
        this.webhooks = webhooks;
    }

    @PostMapping("/mercado-pago")
    public ResponseEntity<Void> receive(
            @RequestBody(required = false) String body,
            @RequestHeader(name = "x-signature", required = false) String signature,
            @RequestHeader(name = "x-request-id", required = false) String requestId,
            HttpServletRequest request
    ) {
        String topic = request.getParameter("topic");
        String queryType = request.getParameter("type");
        if (hasText(topic) || (hasText(queryType) && !"payment".equalsIgnoreCase(queryType))) {
            return ResponseEntity.ok().build();
        }

        String payload = body == null || body.isBlank() ? "{}" : body;
        JsonNode notification = parse(payload);
        String notificationType = hasText(queryType)
                ? queryType
                : notification.path("type").asText(null);
        if (!"payment".equalsIgnoreCase(notificationType)) {
            return ResponseEntity.ok().build();
        }

        String queryPaymentId = request.getParameter("data.id");
        String bodyPaymentId = notification.path("data").path("id").asText(null);
        String paymentId = queryPaymentId == null || queryPaymentId.isBlank() ? bodyPaymentId : queryPaymentId;
        if (queryPaymentId != null && bodyPaymentId != null && !queryPaymentId.equals(bodyPaymentId)) {
            throw new InvalidRequestException("Conflicting Mercado Pago payment IDs.");
        }
        signatures.validate(paymentId, requestId, signature);
        webhooks.process(paymentId, requestId, payload);
        return ResponseEntity.ok().build();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private JsonNode parse(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException exception) {
            throw new InvalidRequestException("Invalid Mercado Pago notification payload.");
        }
    }
}
