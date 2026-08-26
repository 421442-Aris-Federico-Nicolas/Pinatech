package com.computerstore.payment.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.computerstore.payment.config.MercadoPagoEnvironment;
import com.computerstore.payment.config.MercadoPagoProperties;
import com.computerstore.payment.exception.PaymentNotFoundException;
import com.computerstore.payment.exception.PaymentProviderException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RestClientMercadoPagoGatewayTest {

    @Test
    void reportsANotFoundPaymentSeparatelyForWebhookSimulation() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.mercadopago.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClientMercadoPagoGateway gateway = new RestClientMercadoPagoGateway(builder.build(), properties());
        server.expect(requestTo("https://api.mercadopago.com/v1/payments/123"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThrows(PaymentNotFoundException.class, () -> gateway.getPayment("123"));
        server.verify();
    }

    @Test
    void createsAnExpiringSandboxPreferenceWithOnlyImmediatePaymentTypes() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.mercadopago.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MercadoPagoProperties properties = properties();
        RestClientMercadoPagoGateway gateway = new RestClientMercadoPagoGateway(builder.build(), properties);
        UUID attemptId = UUID.randomUUID();
        server.expect(requestTo("https://api.mercadopago.com/checkout/preferences"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Idempotency-Key", attemptId.toString()))
                .andExpect(jsonPath("$.external_reference").value(attemptId.toString()))
                .andExpect(jsonPath("$.notification_url")
                        .value("https://api.example/api/payments/webhooks/mercado-pago"))
                .andExpect(jsonPath("$.back_urls.success")
                        .value("https://store.example/checkout/result?orderId=42"))
                .andExpect(jsonPath("$.binary_mode").value(true))
                .andExpect(jsonPath("$.expires").value(true))
                .andExpect(jsonPath("$.payment_methods.excluded_payment_types[0].id").value("ticket"))
                .andExpect(jsonPath("$.payment_methods.excluded_payment_types[1].id").value("atm"))
                .andExpect(jsonPath("$.payment_methods.excluded_payment_types[2].id").value("bank_transfer"))
                .andRespond(withSuccess(
                        "{\"id\":\"pref-1\",\"init_point\":\"https://prod\","
                                + "\"sandbox_init_point\":\"https://sandbox\"}",
                        MediaType.APPLICATION_JSON));

        PaymentPreference preference = gateway.createPreference(new PaymentPreferenceRequest(
                attemptId,
                42L,
                new BigDecimal("100.00"),
                "ARS",
                Instant.parse("2026-08-17T20:00:00Z"),
                List.of(new PaymentPreferenceRequest.Item("7", "Keyboard - Black", 1,
                        new BigDecimal("100.00")))));

        assertEquals("pref-1", preference.preferenceId());
        assertEquals("https://sandbox", preference.checkoutUrl());
        server.verify();
    }

    @Test
    void resolvesThePreferenceFromTheAuthoritativeMerchantOrder() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.mercadopago.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClientMercadoPagoGateway gateway = new RestClientMercadoPagoGateway(builder.build(), properties());
        server.expect(requestTo("https://api.mercadopago.com/v1/payments/123"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "id": 123,
                          "external_reference": "a472fdf6-7a3d-4859-9f52-e934056e53ce",
                          "collector_id": 99,
                          "transaction_amount": 100.00,
                          "currency_id": "ARS",
                          "status": "approved",
                          "status_detail": "accredited",
                          "live_mode": false,
                          "operation_type": "regular_payment",
                          "transaction_amount_refunded": 0,
                          "date_approved": "2026-08-17T20:00:00Z",
                          "date_last_updated": "2026-08-17T20:00:01Z",
                          "order": {"id": "merchant-order-1", "type": "mercadopago"}
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.mercadopago.com/merchant_orders/merchant-order-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"preference_id\":\"pref-1\"}", MediaType.APPLICATION_JSON));

        ProviderPayment payment = gateway.getPayment("123");

        assertEquals("pref-1", payment.preferenceId());
        assertEquals("99", payment.collectorId());
        assertEquals(0, new BigDecimal("100.00").compareTo(payment.amount()));
        assertEquals(BigDecimal.ZERO, payment.amountRefunded());
        assertEquals(Instant.parse("2026-08-17T20:00:01Z"), payment.lastUpdatedAt());
        server.verify();
    }

    @Test
    void defaultsTheRefundedAmountToZeroWhenANonRefundedPaymentOmitsIt() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.mercadopago.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClientMercadoPagoGateway gateway = new RestClientMercadoPagoGateway(builder.build(), properties());
        server.expect(requestTo("https://api.mercadopago.com/v1/payments/124"))
                .andRespond(withSuccess(paymentJson(
                        "approved", "100.00", "\"date_approved\": \"2026-08-17T20:00:00Z\","),
                        MediaType.APPLICATION_JSON));

        ProviderPayment payment = gateway.getPayment("124");

        assertEquals(BigDecimal.ZERO, payment.amountRefunded());
        server.verify();
    }

    @Test
    void sumsIndividualRefundsWhenTheAggregateFieldIsAbsent() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.mercadopago.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClientMercadoPagoGateway gateway = new RestClientMercadoPagoGateway(builder.build(), properties());
        server.expect(requestTo("https://api.mercadopago.com/v1/payments/125"))
                .andRespond(withSuccess(paymentJson(
                        "refunded", "100.00", "\"refunds\": [{\"amount\": 40}, {\"amount\": 60}],"),
                        MediaType.APPLICATION_JSON));

        ProviderPayment payment = gateway.getPayment("125");

        assertEquals(new BigDecimal("100"), payment.amountRefunded());
        server.verify();
    }

    @Test
    void acceptsTheLegacyRefundedAmountField() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.mercadopago.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClientMercadoPagoGateway gateway = new RestClientMercadoPagoGateway(builder.build(), properties());
        server.expect(requestTo("https://api.mercadopago.com/v1/payments/126"))
                .andRespond(withSuccess(paymentJson(
                        "approved", "100.00",
                        "\"date_approved\": \"2026-08-17T20:00:00Z\", \"amount_refunded\": 25,"),
                        MediaType.APPLICATION_JSON));

        ProviderPayment payment = gateway.getPayment("126");

        assertEquals(new BigDecimal("25"), payment.amountRefunded());
        server.verify();
    }

    @Test
    void rejectsRefundedStatusWithoutTheFullRefundedAmount() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.mercadopago.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClientMercadoPagoGateway gateway = new RestClientMercadoPagoGateway(builder.build(), properties());
        server.expect(requestTo("https://api.mercadopago.com/v1/payments/127"))
                .andRespond(withSuccess(paymentJson("refunded", "100.00", "\"refunds\": [],"),
                        MediaType.APPLICATION_JSON));

        assertThrows(PaymentProviderException.class, () -> gateway.getPayment("127"));
        server.verify();
    }

    @Test
    void rejectsAnApprovedPaymentWithoutApprovalDate() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.mercadopago.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClientMercadoPagoGateway gateway = new RestClientMercadoPagoGateway(builder.build(), properties());
        server.expect(requestTo("https://api.mercadopago.com/v1/payments/128"))
                .andRespond(withSuccess(paymentJson(
                        "approved", "100.00", "\"transaction_amount_refunded\": 0,"),
                        MediaType.APPLICATION_JSON));

        assertThrows(PaymentProviderException.class, () -> gateway.getPayment("128"));
        server.verify();
    }

    @Test
    void rejectsANonNumericTransactionAmount() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.mercadopago.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClientMercadoPagoGateway gateway = new RestClientMercadoPagoGateway(builder.build(), properties());
        server.expect(requestTo("https://api.mercadopago.com/v1/payments/129"))
                .andRespond(withSuccess(paymentJson(
                        "approved", "\"100.00\"",
                        "\"date_approved\": \"2026-08-17T20:00:00Z\", \"transaction_amount_refunded\": 0,"),
                        MediaType.APPLICATION_JSON));

        assertThrows(PaymentProviderException.class, () -> gateway.getPayment("129"));
        server.verify();
    }

    private String paymentJson(String status, String transactionAmount, String additionalFields) {
        return """
                {
                  "id": 124,
                  "external_reference": "a472fdf6-7a3d-4859-9f52-e934056e53ce",
                  "preference_id": "pref-1",
                  "collector_id": 99,
                  "transaction_amount": %s,
                  "currency_id": "ARS",
                  "status": "%s",
                  "status_detail": "accredited",
                  "live_mode": false,
                  "operation_type": "regular_payment",
                  %s
                  "date_last_updated": "2026-08-17T20:00:01Z"
                }
                """.formatted(transactionAmount, status, additionalFields);
    }

    private MercadoPagoProperties properties() {
        return new MercadoPagoProperties(
                true,
                MercadoPagoEnvironment.SANDBOX,
                "TEST-access-token",
                "webhook-secret",
                "99",
                URI.create("https://store.example/"),
                URI.create("https://api.example/"),
                Duration.ofSeconds(1),
                Duration.ofSeconds(2),
                false,
                Duration.ofMinutes(5),
                Duration.ofDays(30));
    }
}
