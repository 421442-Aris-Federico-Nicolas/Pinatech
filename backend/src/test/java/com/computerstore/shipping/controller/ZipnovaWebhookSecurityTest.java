package com.computerstore.shipping.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.computerstore.config.SecurityConfiguration;
import com.computerstore.security.*;
import com.computerstore.shipping.service.ZipnovaWebhookService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ZipnovaWebhookController.class)
@Import({SecurityConfiguration.class, JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class ZipnovaWebhookSecurityTest {
    @MockBean ZipnovaWebhookService webhook;
    @MockBean JwtService jwtService;
    @MockBean CustomUserDetailsService userDetailsService;
    @Autowired MockMvc mvc;

    @Test
    void exactWebhookPatternIsPublic() throws Exception {
        mvc.perform(post("/api/shipping/webhooks/zipnova/a-secret")
                .contentType(MediaType.APPLICATION_JSON).content("{\"shipment_id\":1}"))
                .andExpect(status().isOk());
    }

    @Test
    void unrelatedShippingPostRemainsProtected() throws Exception {
        mvc.perform(post("/api/shipping/webhooks/other/a-secret")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
