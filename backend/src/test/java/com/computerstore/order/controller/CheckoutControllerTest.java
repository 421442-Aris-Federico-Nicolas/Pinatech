package com.computerstore.order.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.computerstore.order.domain.FulfillmentMethod;
import com.computerstore.order.domain.PickupLocationSnapshot;
import com.computerstore.security.CustomUserDetailsService;
import com.computerstore.security.JwtService;
import com.computerstore.order.service.FulfillmentPolicy;
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

    @MockBean
    private FulfillmentPolicy fulfillmentPolicy;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void reportsCheckoutWithoutPaymentOrDeliveryProviders() throws Exception {
        mockMvc.perform(get("/api/checkout/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("ARS"))
                .andExpect(jsonPath("$.orderRequestsEnabled").value(false))
                .andExpect(jsonPath("$.onlinePaymentsEnabled").value(false))
                .andExpect(jsonPath("$.deliveryQuotesEnabled").value(false))
                .andExpect(jsonPath("$.paymentMethods").isEmpty())
                .andExpect(jsonPath("$.deliveryMethods").isEmpty())
                .andExpect(jsonPath("$.fulfillmentMethods").isEmpty())
                .andExpect(jsonPath("$.pickupLocations").isEmpty());
    }

    @Test
    void exposesTheConfiguredPickupPointAndAvailableMethods() throws Exception {
        when(mercadoPagoProperties.enabled()).thenReturn(true);
        when(fulfillmentPolicy.availableMethods()).thenReturn(List.of(FulfillmentMethod.PICKUP));
        when(fulfillmentPolicy.activePickupLocation()).thenReturn(Optional.of(new PickupLocationSnapshot(
                "CORDOBA-CENTRO", "Pinatech Cordoba", List.of("Street 123", "Local 4"),
                "Cordoba", "X", "5000", "Bring your ID.", "Monday to Friday 09:00-18:00")));

        mockMvc.perform(get("/api/checkout/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderRequestsEnabled").value(true))
                .andExpect(jsonPath("$.onlinePaymentsEnabled").value(true))
                .andExpect(jsonPath("$.paymentMethods[0]").value("MERCADO_PAGO"))
                .andExpect(jsonPath("$.fulfillmentMethods[0]").value("PICKUP"))
                .andExpect(jsonPath("$.pickupLocations[0].code").value("CORDOBA-CENTRO"))
                .andExpect(jsonPath("$.pickupLocations[0].addressLines[1]").value("Local 4"))
                .andExpect(jsonPath("$.pickupLocations[0].provinceCode").value("X"));
    }
}
