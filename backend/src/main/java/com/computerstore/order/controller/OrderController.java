package com.computerstore.order.controller;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;

import com.computerstore.catalog.repository.ProductRepository;
import com.computerstore.common.exception.DuplicateResourceException;
import com.computerstore.common.exception.InvalidRequestException;
import com.computerstore.common.exception.ResourceNotFoundException;
import com.computerstore.order.config.OrderProperties;
import com.computerstore.order.domain.CustomerOrder;
import com.computerstore.order.domain.OrderItem;
import com.computerstore.order.dto.CreateOrderRequest;
import com.computerstore.order.dto.OrderResponse;
import com.computerstore.order.repository.CustomerOrderRepository;
import com.computerstore.order.service.OrderStockService;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final CustomerOrderRepository orders;
    private final ProductRepository products;
    private final UserAccountRepository users;
    private final OrderStockService stock;
    private final OrderProperties properties;

    public OrderController(
            CustomerOrderRepository orders,
            ProductRepository products,
            UserAccountRepository users,
            OrderStockService stock,
            OrderProperties properties
    ) {
        this.orders = orders;
        this.products = products;
        this.users = users;
        this.stock = stock;
        this.properties = properties;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<OrderResponse> create(
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String suppliedIdempotencyKey,
            @AuthenticationPrincipal AuthenticatedUser auth
    ) {
        String idempotencyKey = normalizeIdempotencyKey(suppliedIdempotencyKey);
        String requestHash = idempotencyKey == null ? null : requestHash(request);
        var user = users.findByIdForUpdate(auth.id())
                .filter(userCandidate -> userCandidate.isActive())
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        if (idempotencyKey != null) {
            var existing = orders.findByUserIdAndIdempotencyKey(auth.id(), idempotencyKey);
            if (existing.isPresent()) {
                if (!requestHash.equals(existing.get().getRequestHash())) {
                    throw new DuplicateResourceException("Idempotency key was already used for a different order.");
                }
                return ResponseEntity.ok(response(existing.get()));
            }
        }

        var sortedInputs = request.items().stream()
                .sorted(Comparator.comparing(CreateOrderRequest.Item::productId))
                .toList();
        var productIds = new HashSet<Long>();
        var items = new ArrayList<OrderItem>();
        for (var input : sortedInputs) {
            if (!productIds.add(input.productId())) {
                throw new InvalidRequestException("An order cannot contain the same product more than once.");
            }
            var product = products.findById(input.productId())
                    .filter(productCandidate -> productCandidate.isActive())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found."));
            items.add(new OrderItem(product, input.quantity()));
        }

        BigDecimal total = items.stream().map(OrderItem::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        CustomerOrder order = orders.save(new CustomerOrder(
                user,
                items,
                total,
                Instant.now().plus(properties.reservationTtl()),
                idempotencyKey,
                requestHash));
        stock.reserve(order);
        return ResponseEntity.status(HttpStatus.CREATED).body(response(order));
    }

    @GetMapping("/me")
    @Transactional(readOnly = true)
    public List<OrderResponse> mine(@AuthenticationPrincipal AuthenticatedUser auth) {
        return orders.findByUserIdOrderByCreatedAtDesc(auth.id()).stream().map(this::response).toList();
    }

    private String normalizeIdempotencyKey(String suppliedKey) {
        if (suppliedKey == null) {
            return null;
        }
        String key = suppliedKey.trim();
        if (key.isEmpty() || key.length() > 100) {
            throw new InvalidRequestException("Idempotency-Key must contain between 1 and 100 characters.");
        }
        return key;
    }

    private String requestHash(CreateOrderRequest request) {
        String canonicalRequest = request.items().stream()
                .sorted(Comparator.comparing(CreateOrderRequest.Item::productId)
                        .thenComparing(CreateOrderRequest.Item::quantity))
                .map(item -> item.productId() + ":" + item.quantity())
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonicalRequest.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    private OrderResponse response(CustomerOrder order) {
        return new OrderResponse(
                order.getId(),
                order.getStatus().name(),
                order.getTotal(),
                order.getCreatedAt(),
                order.getReservationExpiresAt(),
                order.getUser().getFirstName() + " " + order.getUser().getLastName(),
                order.getUser().getEmail(),
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
