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

import com.computerstore.catalog.repository.ProductVariantRepository;
import com.computerstore.common.exception.DuplicateResourceException;
import com.computerstore.common.exception.InvalidRequestException;
import com.computerstore.common.exception.ResourceNotFoundException;
import com.computerstore.order.config.OrderProperties;
import com.computerstore.order.domain.CustomerOrder;
import com.computerstore.order.domain.OrderItem;
import com.computerstore.order.dto.CreateOrderRequest;
import com.computerstore.order.dto.OrderResponse;
import com.computerstore.order.dto.OrderResponseMapper;
import com.computerstore.order.repository.CustomerOrderRepository;
import com.computerstore.order.service.OrderStockService;
import com.computerstore.security.AuthenticatedUser;
import com.computerstore.user.repository.UserAccountRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final ProductVariantRepository variants;
    private final UserAccountRepository users;
    private final OrderStockService stock;
    private final OrderProperties properties;

    public OrderController(
            CustomerOrderRepository orders,
            ProductVariantRepository variants,
            UserAccountRepository users,
            OrderStockService stock,
            OrderProperties properties
    ) {
        this.orders = orders;
        this.variants = variants;
        this.users = users;
        this.stock = stock;
        this.properties = properties;
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
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
                return ResponseEntity.ok(OrderResponseMapper.toResponse(existing.get()));
            }
        }

        var sortedInputs = request.items().stream()
                .sorted(Comparator.comparing(CreateOrderRequest.Item::variantId))
                .toList();
        var variantIds = new HashSet<Long>();
        var items = new ArrayList<OrderItem>();
        for (var input : sortedInputs) {
            if (!variantIds.add(input.variantId())) {
                throw new InvalidRequestException("An order cannot contain the same product variant more than once.");
            }
            var variant = variants.findByIdAndActiveTrueAndProduct_ActiveTrue(input.variantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product variant not found."));
            items.add(new OrderItem(variant, input.quantity()));
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
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponseMapper.toResponse(order));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional(readOnly = true)
    public List<OrderResponse> mine(@AuthenticationPrincipal AuthenticatedUser auth) {
        return orders.findByUserIdOrderByCreatedAtDesc(auth.id()).stream()
                .map(OrderResponseMapper::toResponse)
                .toList();
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
                .sorted(Comparator.comparing(CreateOrderRequest.Item::variantId)
                        .thenComparing(CreateOrderRequest.Item::quantity))
                .map(item -> item.variantId() + ":" + item.quantity())
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonicalRequest.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

}
