package com.computerstore.order.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.List;
import java.util.Optional;

import com.computerstore.catalog.repository.ProductVariantRepository;
import com.computerstore.config.SecurityConfiguration;
import com.computerstore.order.config.OrderProperties;
import com.computerstore.order.repository.CustomerOrderRepository;
import com.computerstore.order.service.OrderStockService;
import com.computerstore.order.service.FulfillmentPolicy;
import com.computerstore.security.AuthenticatedUser;
import com.computerstore.security.CustomUserDetailsService;
import com.computerstore.security.JwtAuthenticationFilter;
import com.computerstore.security.JwtService;
import com.computerstore.security.RestAccessDeniedHandler;
import com.computerstore.security.RestAuthenticationEntryPoint;
import com.computerstore.user.repository.UserAccountRepository;
import com.computerstore.user.domain.UserAccount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OrderController.class)
@Import({
        SecurityConfiguration.class,
        JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
class OrderControllerSecurityTest {

    @MockBean private CustomerOrderRepository orders;
    @MockBean private ProductVariantRepository variants;
    @MockBean private UserAccountRepository users;
    @MockBean private OrderStockService stock;
    @MockBean private OrderProperties properties;
    @MockBean private FulfillmentPolicy fulfillmentPolicy;
    @MockBean private JwtService jwtService;
    @MockBean private CustomUserDetailsService userDetailsService;

    @Autowired private MockMvc mockMvc;

    @Test
    void orderEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/orders/me"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validOrderRequest()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void orderEndpointsRejectAuthenticatedUsersWithoutCustomerRole() throws Exception {
        AuthenticatedUser admin = principal("ROLE_ADMIN");

        mockMvc.perform(get("/api/orders/me").with(user(admin)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/orders")
                        .with(user(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validOrderRequest()))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerCanListOwnOrders() throws Exception {
        when(orders.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/orders/me").with(user(principal("ROLE_CUSTOMER"))))
                .andExpect(status().isOk());
    }

    @Test
    void checkoutReturnsAStableProblemWhenEmailIsNotVerified() throws Exception {
        UserAccount account = org.mockito.Mockito.mock(UserAccount.class);
        when(account.isActive()).thenReturn(true);
        when(account.isEmailVerified()).thenReturn(false);
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(account));

        mockMvc.perform(post("/api/orders")
                        .with(user(principal("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"variantId":1,"quantity":1}],"paymentMethod":"MERCADO_PAGO",
                                 "fulfillmentMethod":"PICKUP","pickupLocationCode":"CORDOBA-CENTRO",
                                 "pickupLocationVersion":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(
                        "https://computer-store.dev/errors/email-verification-required"))
                .andExpect(jsonPath("$.title").value("Email verification required"));
    }

    private AuthenticatedUser principal(String role) {
        return new AuthenticatedUser(
                1L, "user@example.com", List.of(new SimpleGrantedAuthority(role)));
    }

    private String validOrderRequest() {
        return """
                {"items":[{"variantId":1,"quantity":1}],"paymentMethod":"MERCADO_PAGO",
                 "fulfillmentMethod":"PICKUP","pickupLocationCode":"CORDOBA-CENTRO",
                 "pickupLocationVersion":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}
                """;
    }
}
