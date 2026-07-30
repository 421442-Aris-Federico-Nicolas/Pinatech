package com.computerstore.order.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.computerstore.catalog.repository.ProductRepository;
import com.computerstore.config.SecurityConfiguration;
import com.computerstore.order.config.OrderProperties;
import com.computerstore.order.repository.CustomerOrderRepository;
import com.computerstore.order.service.OrderStockService;
import com.computerstore.security.AuthenticatedUser;
import com.computerstore.security.CustomUserDetailsService;
import com.computerstore.security.JwtAuthenticationFilter;
import com.computerstore.security.JwtService;
import com.computerstore.security.RestAccessDeniedHandler;
import com.computerstore.security.RestAuthenticationEntryPoint;
import com.computerstore.user.repository.UserAccountRepository;
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
    @MockBean private ProductRepository products;
    @MockBean private UserAccountRepository users;
    @MockBean private OrderStockService stock;
    @MockBean private OrderProperties properties;
    @MockBean private JwtService jwtService;
    @MockBean private CustomUserDetailsService userDetailsService;

    @Autowired private MockMvc mockMvc;

    @Test
    void orderEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/orders/me"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":1,\"quantity\":1}]}"))
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
                        .content("{\"items\":[{\"productId\":1,\"quantity\":1}]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerCanListOwnOrders() throws Exception {
        when(orders.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/orders/me").with(user(principal("ROLE_CUSTOMER"))))
                .andExpect(status().isOk());
    }

    private AuthenticatedUser principal(String role) {
        return new AuthenticatedUser(
                1L, "user@example.com", List.of(new SimpleGrantedAuthority(role)));
    }
}
