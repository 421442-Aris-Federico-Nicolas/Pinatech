package com.computerstore.payment.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.computerstore.payment.config.MercadoPagoEnvironment;
import com.computerstore.payment.config.MercadoPagoProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RestClientMercadoPagoGatewayTest {

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
                        .value("https://store.example/api/payments/webhooks/mercado-pago"))
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
        server.verify();
    }

    private MercadoPagoProperties properties() {
        return new MercadoPagoProperties(
                true,
                MercadoPagoEnvironment.SANDBOX,
                "access-token",
                "webhook-secret",
                "99",
                URI.create("https://store.example/"),
                Duration.ofSeconds(1),
                Duration.ofSeconds(2));
    }
}
