package com.computerstore.order.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.computerstore.catalog.domain.Product;
import com.computerstore.catalog.domain.ProductVariant;
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
    void locksIndependentVariantsInStableOrderAndRecordsReservations() {
        InventoryRepository inventories = Mockito.mock(InventoryRepository.class);
        InventoryMovementRepository movements = Mockito.mock(InventoryMovementRepository.class);
        Product product = product(1L);
        ProductVariant firstVariant = variant(1L, product);
        ProductVariant secondVariant = variant(2L, product);
        Inventory firstInventory = inventory(firstVariant);
        Inventory secondInventory = inventory(secondVariant);
        when(inventories.findByVariantIdForUpdate(1L)).thenReturn(Optional.of(firstInventory));
        when(inventories.findByVariantIdForUpdate(2L)).thenReturn(Optional.of(secondInventory));
        CustomerOrder order = new CustomerOrder(
                new UserAccount("Customer", "Example", "customer@example.com", "hash", null),
                List.of(new OrderItem(secondVariant, 2), new OrderItem(firstVariant, 1)),
                new BigDecimal("30.00"));

        new OrderStockService(inventories, movements).reserve(order);

        var lockOrder = inOrder(inventories);
        lockOrder.verify(inventories).findByVariantIdForUpdate(1L);
        lockOrder.verify(inventories).findByVariantIdForUpdate(2L);
        assertEquals(1, firstInventory.getReservedQuantity());
        assertEquals(2, secondInventory.getReservedQuantity());
        Mockito.verify(movements, Mockito.times(2)).save(Mockito.any());
    }

    @Test
    void restoresConsumedStockWhenAnOrderIsCancelled() {
        InventoryRepository inventories = Mockito.mock(InventoryRepository.class);
        InventoryMovementRepository movements = Mockito.mock(InventoryMovementRepository.class);
        Product product = product(1L);
        ProductVariant variant = variant(1L, product);
        Inventory inventory = inventory(variant);
        when(inventories.findByVariantIdForUpdate(1L)).thenReturn(Optional.of(inventory));
        CustomerOrder order = new CustomerOrder(
                new UserAccount("Customer", "Example", "customer@example.com", "hash", null),
                List.of(new OrderItem(variant, 2)),
                new BigDecimal("20.00"));
        OrderStockService service = new OrderStockService(inventories, movements);
        service.reserve(order);
        service.consume(order);

        service.restore(order);

        assertEquals(10, inventory.getAvailableQuantity());
        assertEquals(0, inventory.getReservedQuantity());
        Mockito.verify(movements, Mockito.times(3)).save(Mockito.any());
    }

    private Product product(Long id) {
        Product product = Mockito.mock(Product.class);
        when(product.getId()).thenReturn(id);
        when(product.getName()).thenReturn("Product " + id);
        when(product.getPrice()).thenReturn(BigDecimal.TEN);
        return product;
    }

    private ProductVariant variant(Long id, Product product) {
        ProductVariant variant = Mockito.mock(ProductVariant.class);
        when(variant.getId()).thenReturn(id);
        when(variant.getProduct()).thenReturn(product);
        when(variant.getColorName()).thenReturn("Black");
        return variant;
    }

    private Inventory inventory(ProductVariant variant) {
        Inventory inventory = new Inventory(variant);
        inventory.adjust(10);
        return inventory;
    }
}
