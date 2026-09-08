package com.computerstore.order.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.computerstore.config.SecurityConfiguration;
import com.computerstore.email.OrderEmailOutboxService;
import com.computerstore.order.domain.CustomerOrder;
import com.computerstore.order.repository.CustomerOrderRepository;
import com.computerstore.order.service.OrderStockService;
import com.computerstore.security.CustomUserDetailsService;
import com.computerstore.security.JwtAuthenticationFilter;
import com.computerstore.security.JwtService;
import com.computerstore.security.RestAccessDeniedHandler;
import com.computerstore.security.RestAuthenticationEntryPoint;
import com.computerstore.user.domain.UserAccount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminOrderController.class)
@Import({SecurityConfiguration.class, JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class AdminOrderDetailControllerTest {
    @MockBean private CustomerOrderRepository orders;
    @MockBean private OrderStockService stock;
    @MockBean private OrderEmailOutboxService outbox;
    @MockBean private JwtService jwtService;
    @MockBean private CustomUserDetailsService userDetailsService;
    @Autowired private MockMvc mockMvc;

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/orders/41")).andExpect(status().isUnauthorized());
        verifyNoInteractions(orders);
    }

    @Test
    void rejectsNonAdmins() throws Exception {
        mockMvc.perform(get("/api/admin/orders/41").with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(orders);
    }

    @Test
    void adminReadsOnlyTheRequestedOrder() throws Exception {
        var order = new CustomerOrder(new UserAccount("Ada", "Lovelace", "ada@example.com", "hash", null),
                List.of(), BigDecimal.TEN);
        ReflectionTestUtils.setField(order, "id", 41L);
        when(orders.findDetailsById(41L)).thenReturn(Optional.of(order));

        mockMvc.perform(get("/api/admin/orders/41").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(41))
                .andExpect(jsonPath("$.customerName").value("Ada Lovelace"))
                .andExpect(jsonPath("$.items").isArray());
        verify(orders).findDetailsById(41L);
        verifyNoMoreInteractions(orders);
    }

    @Test
    void missingOrderReturns404WithoutListingOrders() throws Exception {
        when(orders.findDetailsById(404L)).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/admin/orders/404").with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());
        verify(orders).findDetailsById(404L);
        verifyNoMoreInteractions(orders);
    }
}
