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
        order.getItems().stream().sorted(byProductId()).forEach(item -> {
            inventory(item).reserve(item.getQuantity());
            movements.save(InventoryMovement.reservation(item.getProduct(), item.getQuantity(), order));
        });
    }

    public void release(CustomerOrder order) {
        order.getItems().stream().sorted(byProductId()).forEach(item -> {
            inventory(item).release(item.getQuantity());
            movements.save(InventoryMovement.release(item.getProduct(), item.getQuantity(), order));
        });
    }

    public void consume(CustomerOrder order) {
        order.getItems().stream().sorted(byProductId()).forEach(item -> {
            inventory(item).consumeReserved(item.getQuantity());
            movements.save(InventoryMovement.consumption(item.getProduct(), item.getQuantity(), order));
        });
    }

    private com.computerstore.inventory.domain.Inventory inventory(OrderItem item) {
        return inventories.findByProductIdForUpdate(item.getProduct().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found."));
    }

    private Comparator<OrderItem> byProductId() {
        return Comparator.comparing(item -> item.getProduct().getId());
    }
}
