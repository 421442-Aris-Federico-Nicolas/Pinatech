package com.computerstore.order.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.computerstore.catalog.domain.Product;
import com.computerstore.order.domain.CustomerOrder;
import com.computerstore.order.domain.FulfillmentStatus;
import com.computerstore.order.domain.OrderItem;
import com.computerstore.order.domain.OrderStatus;
import com.computerstore.order.domain.PaymentStatus;
import com.computerstore.order.repository.CustomerOrderRepository;
import com.computerstore.user.domain.UserAccount;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OrderExpirationWorkerTest {

    @Test
    void releasesAndCancelsTheNextExpiredReservation() {
        CustomerOrderRepository orders = Mockito.mock(CustomerOrderRepository.class);
        OrderStockService stock = Mockito.mock(OrderStockService.class);
        Product product = Mockito.mock(Product.class);
        when(product.getName()).thenReturn("Keyboard");
        when(product.getPrice()).thenReturn(BigDecimal.TEN);
        CustomerOrder order = new CustomerOrder(
                new UserAccount("Customer", "Example", "customer@example.com", "hash", null),
                List.of(new OrderItem(product, 1)),
                BigDecimal.TEN,
                Instant.now().minusSeconds(1),
                null,
                null);
        when(orders.findNextExpiredPendingIdForUpdate(any())).thenReturn(Optional.of(9L));
        when(orders.findById(9L)).thenReturn(Optional.of(order));

        boolean expired = new OrderExpirationWorker(orders, stock).expireNext();

        assertTrue(expired);
        verify(stock).release(order);
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals(PaymentStatus.EXPIRED, order.getPaymentStatus());
        assertEquals(FulfillmentStatus.CANCELLED, order.getFulfillmentStatus());
    }
}
