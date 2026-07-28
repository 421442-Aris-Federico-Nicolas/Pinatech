package com.computerstore.order.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.computerstore.catalog.domain.Product;
import com.computerstore.inventory.domain.Inventory;
import com.computerstore.inventory.repository.InventoryMovementRepository;
import com.computerstore.inventory.repository.InventoryRepository;
import com.computerstore.order.domain.CustomerOrder;
import com.computerstore.order.domain.OrderItem;
import com.computerstore.user.domain.UserAccount;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OrderStockServiceTest {

    @Test
    void locksInventoryInStableProductOrderAndRecordsReservations() {
        InventoryRepository inventories = Mockito.mock(InventoryRepository.class);
        InventoryMovementRepository movements = Mockito.mock(InventoryMovementRepository.class);
        Product firstProduct = product(1L);
        Product secondProduct = product(2L);
        Inventory firstInventory = inventory(firstProduct);
        Inventory secondInventory = inventory(secondProduct);
        when(inventories.findByProductIdForUpdate(1L)).thenReturn(Optional.of(firstInventory));
        when(inventories.findByProductIdForUpdate(2L)).thenReturn(Optional.of(secondInventory));
        CustomerOrder order = new CustomerOrder(
                new UserAccount("Customer", "Example", "customer@example.com", "hash", null),
                List.of(new OrderItem(secondProduct, 2), new OrderItem(firstProduct, 1)),
                new BigDecimal("30.00"));

        new OrderStockService(inventories, movements).reserve(order);

        var lockOrder = inOrder(inventories);
        lockOrder.verify(inventories).findByProductIdForUpdate(1L);
        lockOrder.verify(inventories).findByProductIdForUpdate(2L);
        assertEquals(1, firstInventory.getReservedQuantity());
        assertEquals(2, secondInventory.getReservedQuantity());
        Mockito.verify(movements, Mockito.times(2)).save(Mockito.any());
    }

    private Product product(Long id) {
        Product product = Mockito.mock(Product.class);
        when(product.getId()).thenReturn(id);
        when(product.getName()).thenReturn("Product " + id);
        when(product.getPrice()).thenReturn(BigDecimal.TEN);
        return product;
    }

    private Inventory inventory(Product product) {
        Inventory inventory = new Inventory(product);
        inventory.adjust(10);
        return inventory;
    }
}
