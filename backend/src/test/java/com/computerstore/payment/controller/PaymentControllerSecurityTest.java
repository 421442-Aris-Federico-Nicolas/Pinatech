package com.computerstore.payment.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.computerstore.config.SecurityConfiguration;
import com.computerstore.payment.dto.PaymentCheckoutResponse;
import com.computerstore.payment.exception.PaymentNotFoundException;
import com.computerstore.payment.service.MercadoPagoSignatureValidator;
import com.computerstore.payment.service.PaymentCheckoutService;
import com.computerstore.payment.service.PaymentWebhookService;
import com.computerstore.security.AuthenticatedUser;
import com.computerstore.security.CustomUserDetailsService;
import com.computerstore.security.JwtAuthenticationFilter;
import com.computerstore.security.JwtService;
import com.computerstore.security.RestAccessDeniedHandler;
import com.computerstore.security.RestAuthenticationEntryPoint;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({PaymentCheckoutController.class, MercadoPagoWebhookController.class})
@Import({
        SecurityConfiguration.class,
        JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
class PaymentControllerSecurityTest {

    @MockBean private PaymentCheckoutService checkout;
    @MockBean private PaymentWebhookService webhooks;
    @MockBean private MercadoPagoSignatureValidator signatures;
    @MockBean private JwtService jwtService;
    @MockBean private CustomUserDetailsService userDetailsService;
    @Autowired private MockMvc mockMvc;

    @Test
    void checkoutRequiresACustomer() throws Exception {
        mockMvc.perform(post("/api/orders/1/payments/mercado-pago")
                        .header("Idempotency-Key", "key-1"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/orders/1/payments/mercado-pago")
                        .with(user(principal("ROLE_ADMIN")))
                        .header("Idempotency-Key", "key-1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerCanCreateCheckout() throws Exception {
        when(checkout.create(anyLong(), anyLong(), anyString())).thenReturn(
                new PaymentCheckoutService.CheckoutResult(true, new PaymentCheckoutResponse(
                        UUID.randomUUID(), 1L, "PREFERENCE_CREATED", "https://sandbox", Instant.now())));

        mockMvc.perform(post("/api/orders/1/payments/mercado-pago")
                        .with(user(principal("ROLE_CUSTOMER")))
                        .header("Idempotency-Key", "key-1"))
                .andExpect(status().isCreated());
    }

    @Test
    void webhookIsPublicAtTheJwtLayer() throws Exception {
        doNothing().when(signatures).validate(anyString(), anyString(), anyString());
        doNothing().when(webhooks).process(anyString(), anyString(), anyString());

        mockMvc.perform(post("/api/payments/webhooks/mercado-pago")
                        .queryParam("type", "payment")
                        .queryParam("data.id", "123")
                        .header("x-signature", "ts=1,v1=hash")
                        .header("x-request-id", "request-1")
                        .contentType("application/json")
                        .content("{\"data\":{\"id\":\"123\"}}"))
                .andExpect(status().isOk());

        verify(signatures).validate("123", "request-1", "ts=1,v1=hash");
        verify(webhooks).process("123", "request-1", "{\"data\":{\"id\":\"123\"}}");
    }

    @Test
    void acceptsAValidWebhookWhenTheSimulatorPaymentDoesNotExist() throws Exception {
        doNothing().when(signatures).validate(anyString(), anyString(), anyString());
        doThrow(new PaymentNotFoundException("not found", new RuntimeException()))
                .when(webhooks).process(anyString(), anyString(), anyString());

        mockMvc.perform(post("/api/payments/webhooks/mercado-pago")
                        .queryParam("type", "payment")
                        .queryParam("data.id", "123")
                        .header("x-signature", "ts=1,v1=hash")
                        .header("x-request-id", "request-1")
                        .contentType("application/json")
                        .content("{\"data\":{\"id\":\"123\"}}"))
                .andExpect(status().isAccepted());
    }

    @Test
    void ignoresMerchantOrderNotifications() throws Exception {
        mockMvc.perform(post("/api/payments/webhooks/mercado-pago")
                        .queryParam("data.id", "43733831810")
                        .queryParam("type", "topic_merchant_order_wh")
                        .contentType("application/json")
                        .content("{\"data\":{\"id\":\"43733831810\"}}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/payments/webhooks/mercado-pago")
                        .queryParam("id", "43733831810")
                        .queryParam("topic", "merchant_order"))
                .andExpect(status().isOk());

        verify(signatures, never()).validate(any(), any(), any());
        verify(webhooks, never()).process(any(), any(), any());
    }

    private AuthenticatedUser principal(String role) {
        return new AuthenticatedUser(1L, "user@example.com",
                List.of(new SimpleGrantedAuthority(role)));
    }
}
