package com.computerstore.order.service;

import java.util.Comparator;

import com.computerstore.common.exception.ResourceNotFoundException;
import com.computerstore.inventory.domain.InventoryMovement;
import com.computerstore.inventory.repository.InventoryMovementRepository;
import com.computerstore.inventory.repository.InventoryRepository;
import com.computerstore.order.domain.CustomerOrder;
import com.computerstore.order.domain.OrderItem;
import org.springframework.stereotype.Service;

@Service
public class OrderStockService {

    private final InventoryRepository inventories;
    private final InventoryMovementRepository movements;

    public OrderStockService(InventoryRepository inventories, InventoryMovementRepository movements) {
        this.inventories = inventories;
        this.movements = movements;
    }

    public void reserve(CustomerOrder order) {
        order.getItems().stream().sorted(byVariantId()).forEach(item -> {
            inventory(item).reserve(item.getQuantity());
            movements.save(InventoryMovement.reservation(item.getVariant(), item.getQuantity(), order));
        });
    }

    public void release(CustomerOrder order) {
        order.getItems().stream().sorted(byVariantId()).forEach(item -> {
            inventory(item).release(item.getQuantity());
            movements.save(InventoryMovement.release(item.getVariant(), item.getQuantity(), order));
        });
    }

    public void consume(CustomerOrder order) {
        order.getItems().stream().sorted(byVariantId()).forEach(item -> {
            inventory(item).consumeReserved(item.getQuantity());
            movements.save(InventoryMovement.consumption(item.getVariant(), item.getQuantity(), order));
        });
    }

    public void restore(CustomerOrder order) {
        order.getItems().stream().sorted(byVariantId()).forEach(item -> {
            inventory(item).restore(item.getQuantity());
            movements.save(InventoryMovement.returnedFromCancelledOrder(
                    item.getVariant(), item.getQuantity(), order));
        });
    }

    private com.computerstore.inventory.domain.Inventory inventory(OrderItem item) {
        return inventories.findByVariantIdForUpdate(item.getVariant().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found."));
    }

    private Comparator<OrderItem> byVariantId() {
        return Comparator.comparing(item -> item.getVariant().getId());
    }
}
