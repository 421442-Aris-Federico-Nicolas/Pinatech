package com.computerstore.order.controller;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.computerstore.catalog.repository.ProductRepository;
import com.computerstore.common.exception.ResourceNotFoundException;
import com.computerstore.inventory.repository.InventoryRepository;
import com.computerstore.order.domain.CustomerOrder;
import com.computerstore.order.domain.OrderItem;
import com.computerstore.order.dto.CreateOrderRequest;
import com.computerstore.order.dto.OrderResponse;
import com.computerstore.order.repository.CustomerOrderRepository;
import com.computerstore.security.AuthenticatedUser;
import com.computerstore.user.repository.UserAccountRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final CustomerOrderRepository orders;
    private final ProductRepository products;
    private final InventoryRepository inventory;
    private final UserAccountRepository users;

    public OrderController(
            CustomerOrderRepository orders,
            ProductRepository products,
            InventoryRepository inventory,
            UserAccountRepository users
    ) {
        this.orders = orders;
        this.products = products;
        this.inventory = inventory;
        this.users = users;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<OrderResponse> create(
            @Valid @RequestBody CreateOrderRequest request,
            @AuthenticationPrincipal AuthenticatedUser auth
    ) {
        var user = users.findById(auth.id())
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
        var items = new ArrayList<OrderItem>();
        for (var input : request.items()) {
            var product = products.findById(input.productId())
                    .filter(productCandidate -> productCandidate.isActive())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found."));
            inventory.findByProductIdForUpdate(product.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Inventory not found."))
                    .reserve(input.quantity());
            items.add(new OrderItem(product, input.quantity()));
        }
        BigDecimal total = items.stream().map(OrderItem::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        CustomerOrder order = orders.save(new CustomerOrder(user, items, total));
        return ResponseEntity.status(HttpStatus.CREATED).body(response(order));
    }

    @GetMapping("/me")
    @Transactional(readOnly = true)
    public List<OrderResponse> mine(@AuthenticationPrincipal AuthenticatedUser auth) {
        return orders.findByUserIdOrderByCreatedAtDesc(auth.id()).stream().map(this::response).toList();
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
