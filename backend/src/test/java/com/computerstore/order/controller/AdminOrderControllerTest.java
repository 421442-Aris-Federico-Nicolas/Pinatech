package com.computerstore.order.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.computerstore.catalog.domain.Product;
import com.computerstore.order.domain.CustomerOrder;
import com.computerstore.order.domain.OrderItem;
import com.computerstore.order.domain.OrderStatus;
import com.computerstore.order.dto.OrderStatusRequest;
import com.computerstore.order.repository.CustomerOrderRepository;
import com.computerstore.order.service.OrderStockService;
import com.computerstore.user.domain.UserAccount;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AdminOrderControllerTest {

    @Test
    void cancellationReleasesPreviouslyReservedStock() {
        CustomerOrderRepository orders = Mockito.mock(CustomerOrderRepository.class);
        OrderStockService stock = Mockito.mock(OrderStockService.class);
        AdminOrderController controller = new AdminOrderController(orders, stock);
        CustomerOrder order = paidOrder();
        when(orders.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        controller.status(1L, new OrderStatusRequest(OrderStatus.CANCELLED));

        verify(stock).release(order);
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
    }

    @Test
    void preparingConsumesTheReservation() {
        CustomerOrderRepository orders = Mockito.mock(CustomerOrderRepository.class);
        OrderStockService stock = Mockito.mock(OrderStockService.class);
        AdminOrderController controller = new AdminOrderController(orders, stock);
        CustomerOrder order = paidOrder();
        when(orders.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        controller.status(1L, new OrderStatusRequest(OrderStatus.PREPARING));

        verify(stock).consume(order);
        assertEquals(OrderStatus.PREPARING, order.getStatus());
    }

    private CustomerOrder paidOrder() {
        Product product = Mockito.mock(Product.class);
        when(product.getId()).thenReturn(7L);
        when(product.getName()).thenReturn("Keyboard");
        when(product.getPrice()).thenReturn(BigDecimal.TEN);
        CustomerOrder order = new CustomerOrder(
                new UserAccount("Customer", "Example", "customer@example.com", "hash", null),
                List.of(new OrderItem(product, 2)),
                new BigDecimal("20.00"));
        order.transitionTo(OrderStatus.PAID);
        return order;
    }
}
