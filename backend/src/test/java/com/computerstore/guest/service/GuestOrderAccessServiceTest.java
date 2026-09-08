package com.computerstore.guest.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.computerstore.common.exception.ResourceNotFoundException;
import com.computerstore.order.domain.CustomerOrder;
import com.computerstore.order.repository.CustomerOrderRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GuestOrderAccessServiceTest {
    @Test
    void returnsUniformNotFoundForAnIncorrectCredential() {
        CustomerOrderRepository orders = mock(CustomerOrderRepository.class);
        GuestRequestSecurity security = mock(GuestRequestSecurity.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        CustomerOrder order = mock(CustomerOrder.class);
        UUID publicId = UUID.randomUUID();
        when(orders.findByPublicId(publicId)).thenReturn(Optional.of(order));
        when(order.isGuest()).thenReturn(true);
        when(order.getGuestAccessTokenHash()).thenReturn("a".repeat(64));
        when(security.orderAccessHash(request)).thenReturn("b".repeat(64));

        assertThrows(ResourceNotFoundException.class,
                () -> new GuestOrderAccessService(orders, security).require(publicId, request, false));
    }
}
