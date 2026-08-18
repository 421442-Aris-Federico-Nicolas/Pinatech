package com.computerstore.order.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.computerstore.catalog.domain.Product;
import com.computerstore.catalog.domain.ProductVariant;
import com.computerstore.order.domain.CustomerOrder;
import com.computerstore.order.domain.FulfillmentStatus;
import com.computerstore.order.domain.OrderItem;
import com.computerstore.order.domain.OrderStatus;
import com.computerstore.order.domain.PaymentStatus;
import com.computerstore.order.dto.OrderStatusRequest;
import com.computerstore.order.repository.CustomerOrderRepository;
import com.computerstore.order.service.OrderStockService;
import com.computerstore.user.domain.UserAccount;
import com.computerstore.common.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AdminOrderControllerTest {

    @Test
    void blocksCancellationOfAPaidOrder() {
        CustomerOrderRepository orders = Mockito.mock(CustomerOrderRepository.class);
        OrderStockService stock = Mockito.mock(OrderStockService.class);
        AdminOrderController controller = new AdminOrderController(orders, stock);
        CustomerOrder order = paidOrder();
        when(orders.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        assertThrows(InvalidStateTransitionException.class,
                () -> controller.status(1L, new OrderStatusRequest(OrderStatus.CANCELLED)));

        verify(stock, never()).release(order);
        assertEquals(OrderStatus.PAID, order.getStatus());
        assertEquals(PaymentStatus.APPROVED, order.getPaymentStatus());
        assertEquals(FulfillmentStatus.PENDING, order.getFulfillmentStatus());
    }

    @Test
    void blocksManualPaymentApproval() {
        CustomerOrderRepository orders = Mockito.mock(CustomerOrderRepository.class);
        OrderStockService stock = Mockito.mock(OrderStockService.class);
        AdminOrderController controller = new AdminOrderController(orders, stock);

        assertThrows(InvalidStateTransitionException.class,
                () -> controller.status(1L, new OrderStatusRequest(OrderStatus.PAID)));

        verify(orders, never()).findByIdForUpdate(1L);
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
        assertEquals(PaymentStatus.APPROVED, order.getPaymentStatus());
        assertEquals(FulfillmentStatus.PREPARING, order.getFulfillmentStatus());
    }

    @Test
    void blocksCancellationOfAPreparingPaidOrder() {
        CustomerOrderRepository orders = Mockito.mock(CustomerOrderRepository.class);
        OrderStockService stock = Mockito.mock(OrderStockService.class);
        AdminOrderController controller = new AdminOrderController(orders, stock);
        CustomerOrder order = paidOrder();
        order.transitionTo(OrderStatus.PREPARING);
        when(orders.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        assertThrows(InvalidStateTransitionException.class,
                () -> controller.status(1L, new OrderStatusRequest(OrderStatus.CANCELLED)));

        verify(stock, never()).restore(order);
        assertEquals(OrderStatus.PREPARING, order.getStatus());
        assertEquals(PaymentStatus.APPROVED, order.getPaymentStatus());
        assertEquals(FulfillmentStatus.PREPARING, order.getFulfillmentStatus());
    }

    private CustomerOrder paidOrder() {
        Product product = Mockito.mock(Product.class);
        when(product.getId()).thenReturn(7L);
        when(product.getName()).thenReturn("Keyboard");
        when(product.getPrice()).thenReturn(BigDecimal.TEN);
        ProductVariant variant = Mockito.mock(ProductVariant.class);
        when(variant.getProduct()).thenReturn(product);
        when(variant.getColorName()).thenReturn("Black");
        CustomerOrder order = new CustomerOrder(
                new UserAccount("Customer", "Example", "customer@example.com", "hash", null),
                List.of(new OrderItem(variant, 2)),
                new BigDecimal("20.00"));
        order.transitionTo(OrderStatus.PAID);
        return order;
    }
}
