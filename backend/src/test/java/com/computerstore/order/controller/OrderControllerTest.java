package com.computerstore.order.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.computerstore.catalog.domain.Product;
import com.computerstore.catalog.repository.ProductRepository;
import com.computerstore.inventory.domain.Inventory;
import com.computerstore.inventory.repository.InventoryRepository;
import com.computerstore.order.domain.CustomerOrder;
import com.computerstore.order.dto.CreateOrderRequest;
import com.computerstore.order.repository.CustomerOrderRepository;
import com.computerstore.security.AuthenticatedUser;
import com.computerstore.user.domain.UserAccount;
import com.computerstore.user.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;

class OrderControllerTest {

    @Test
    void recalculatesTotalAndReservesStockThroughThePessimisticLock() {
        CustomerOrderRepository orders = Mockito.mock(CustomerOrderRepository.class);
        ProductRepository products = Mockito.mock(ProductRepository.class);
        InventoryRepository inventoryRepository = Mockito.mock(InventoryRepository.class);
        UserAccountRepository users = Mockito.mock(UserAccountRepository.class);
        OrderController controller = new OrderController(orders, products, inventoryRepository, users);
        Product product = Mockito.mock(Product.class);
        when(product.getId()).thenReturn(7L);
        when(product.isActive()).thenReturn(true);
        when(product.getName()).thenReturn("Keyboard");
        when(product.getPrice()).thenReturn(new BigDecimal("125.50"));
        Inventory inventory = new Inventory(product);
        inventory.adjust(3);
        when(users.findById(1L)).thenReturn(Optional.of(new UserAccount("Customer", "Example", "customer@example.com", "hash", null)));
        when(products.findById(7L)).thenReturn(Optional.of(product));
        when(inventoryRepository.findByProductIdForUpdate(7L)).thenReturn(Optional.of(inventory));
        when(orders.save(any(CustomerOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = controller.create(
                new CreateOrderRequest(List.of(new CreateOrderRequest.Item(7L, 2))),
                new AuthenticatedUser(1L, "customer@example.com", List.of())
        );

        ArgumentCaptor<CustomerOrder> savedOrder = ArgumentCaptor.forClass(CustomerOrder.class);
        verify(orders).save(savedOrder.capture());
        verify(inventoryRepository).findByProductIdForUpdate(7L);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(new BigDecimal("251.00"), savedOrder.getValue().getTotal());
        assertEquals(1, inventory.getAvailableQuantity());
        assertEquals(2, inventory.getReservedQuantity());
    }
}
