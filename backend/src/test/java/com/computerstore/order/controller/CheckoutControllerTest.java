package com.computerstore.order.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.computerstore.security.CustomUserDetailsService;
import com.computerstore.security.JwtService;
import com.computerstore.payment.config.MercadoPagoProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CheckoutController.class)
@AutoConfigureMockMvc(addFilters = false)
class CheckoutControllerTest {

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    @MockBean
    private MercadoPagoProperties mercadoPagoProperties;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void reportsCheckoutWithoutPaymentOrDeliveryProviders() throws Exception {
        mockMvc.perform(get("/api/checkout/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("ARS"))
                .andExpect(jsonPath("$.orderRequestsEnabled").value(true))
                .andExpect(jsonPath("$.onlinePaymentsEnabled").value(false))
                .andExpect(jsonPath("$.deliveryQuotesEnabled").value(false))
                .andExpect(jsonPath("$.paymentMethods").isEmpty())
                .andExpect(jsonPath("$.deliveryMethods").isEmpty());
    }
}
