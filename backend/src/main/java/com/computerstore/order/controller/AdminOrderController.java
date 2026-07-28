package com.computerstore.order.controller;

import java.util.List;

import com.computerstore.common.exception.ResourceNotFoundException;
import com.computerstore.inventory.repository.InventoryRepository;
import com.computerstore.order.domain.CustomerOrder;
import com.computerstore.order.domain.OrderStatus;
import com.computerstore.order.dto.OrderResponse;
import com.computerstore.order.dto.OrderStatusRequest;
import com.computerstore.order.repository.CustomerOrderRepository;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/orders")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderController {

    private final CustomerOrderRepository orders;
    private final InventoryRepository inventory;

    public AdminOrderController(CustomerOrderRepository orders, InventoryRepository inventory) {
        this.orders = orders;
        this.inventory = inventory;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<OrderResponse> list() {
        return orders.findAll().stream().map(this::response).toList();
    }

    @PatchMapping("/{id}/status")
    @Transactional
    public OrderResponse status(@PathVariable Long id, @Valid @RequestBody OrderStatusRequest request) {
        var order = orders.findById(id).orElseThrow(() -> new ResourceNotFoundException("Order not found."));
        OrderStatus previous = order.getStatus();
        order.transitionTo(request.status());
        if (request.status() == OrderStatus.CANCELLED && previous != OrderStatus.CANCELLED) {
            order.getItems().forEach(item -> inventory.findByProductIdForUpdate(item.getProduct().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Inventory not found."))
                    .release(item.getQuantity()));
        }
        return response(order);
    }

    private OrderResponse response(CustomerOrder order) {
        return new OrderResponse(
                order.getId(),
                order.getStatus().name(),
                order.getTotal(),
                order.getCreatedAt(),
                order.getItems().stream()
                        .map(item -> new OrderResponse.Item(
                                item.getProduct().getId(),
                                item.getProductName(),
                                item.getUnitPrice(),
                                item.getQuantity(),
                                item.getSubtotal()))
                        .toList()
        );
    }
}
