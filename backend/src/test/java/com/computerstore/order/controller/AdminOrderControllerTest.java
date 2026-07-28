package com.computerstore.order.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.computerstore.catalog.domain.Product;
import com.computerstore.inventory.domain.Inventory;
import com.computerstore.inventory.repository.InventoryRepository;
import com.computerstore.order.domain.CustomerOrder;
import com.computerstore.order.domain.OrderItem;
import com.computerstore.order.domain.OrderStatus;
import com.computerstore.order.dto.OrderStatusRequest;
import com.computerstore.order.repository.CustomerOrderRepository;
import com.computerstore.user.domain.UserAccount;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AdminOrderControllerTest {

    @Test
    void cancellationReleasesPreviouslyReservedStock() {
        CustomerOrderRepository orders = Mockito.mock(CustomerOrderRepository.class);
        InventoryRepository inventories = Mockito.mock(InventoryRepository.class);
        AdminOrderController controller = new AdminOrderController(orders, inventories);
        Product product = Mockito.mock(Product.class);
        when(product.getId()).thenReturn(7L);
        when(product.getName()).thenReturn("Keyboard");
        when(product.getPrice()).thenReturn(BigDecimal.TEN);
        Inventory inventory = new Inventory(product);
        inventory.adjust(5);
        inventory.reserve(2);
        CustomerOrder order = new CustomerOrder(
                new UserAccount("Customer", "Example", "customer@example.com", "hash", null),
                List.of(new OrderItem(product, 2)),
                new BigDecimal("20.00")
        );
        order.transitionTo(OrderStatus.PAID);
        when(orders.findById(1L)).thenReturn(Optional.of(order));
        when(inventories.findByProductIdForUpdate(7L)).thenReturn(Optional.of(inventory));

        controller.status(1L, new OrderStatusRequest(OrderStatus.CANCELLED));

        verify(inventories).findByProductIdForUpdate(7L);
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals(5, inventory.getAvailableQuantity());
        assertEquals(0, inventory.getReservedQuantity());
    }
}
