package com.computerstore.order.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.computerstore.catalog.domain.Product;
import com.computerstore.catalog.repository.ProductRepository;
import com.computerstore.common.exception.DuplicateResourceException;
import com.computerstore.order.config.OrderProperties;
import com.computerstore.order.domain.CustomerOrder;
import com.computerstore.order.dto.CreateOrderRequest;
import com.computerstore.order.repository.CustomerOrderRepository;
import com.computerstore.order.service.OrderStockService;
import com.computerstore.security.AuthenticatedUser;
import com.computerstore.user.domain.UserAccount;
import com.computerstore.user.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;

class OrderControllerTest {

    private final CustomerOrderRepository orders = Mockito.mock(CustomerOrderRepository.class);
    private final ProductRepository products = Mockito.mock(ProductRepository.class);
    private final UserAccountRepository users = Mockito.mock(UserAccountRepository.class);
    private final OrderStockService stock = Mockito.mock(OrderStockService.class);
    private final OrderController controller = new OrderController(
            orders, products, users, stock, new OrderProperties(Duration.ofMinutes(15), 100));
    private final AuthenticatedUser authenticatedUser =
            new AuthenticatedUser(1L, "customer@example.com", List.of());

    @BeforeEach
    void setUp() {
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(
                new UserAccount("Customer", "Example", "customer@example.com", "hash", null)));
    }

    @Test
    void recalculatesTotalAndDelegatesTheStockReservation() {
        Product product = product(7L, "Keyboard", "125.50");
        when(products.findById(7L)).thenReturn(Optional.of(product));
        when(orders.save(any(CustomerOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = controller.create(
                new CreateOrderRequest(List.of(new CreateOrderRequest.Item(7L, 2))),
                null,
                authenticatedUser);

        ArgumentCaptor<CustomerOrder> savedOrder = ArgumentCaptor.forClass(CustomerOrder.class);
        verify(orders).save(savedOrder.capture());
        verify(stock).reserve(savedOrder.getValue());
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(new BigDecimal("251.00"), savedOrder.getValue().getTotal());
        assertEquals("ARS", response.getBody().currency());
        assertEquals("PENDING", response.getBody().paymentStatus());
        assertEquals("PENDING", response.getBody().fulfillmentStatus());
    }

    @Test
    void returnsTheOriginalOrderForAnIdempotentRetryWithoutReservingAgain() {
        Product product = product(7L, "Keyboard", "125.50");
        AtomicReference<CustomerOrder> saved = new AtomicReference<>();
        when(products.findById(7L)).thenReturn(Optional.of(product));
        when(orders.findByUserIdAndIdempotencyKey(1L, "checkout-1"))
                .thenAnswer(invocation -> Optional.ofNullable(saved.get()));
        when(orders.save(any(CustomerOrder.class))).thenAnswer(invocation -> {
            saved.set(invocation.getArgument(0));
            return saved.get();
        });
        var request = new CreateOrderRequest(List.of(new CreateOrderRequest.Item(7L, 2)));

        var created = controller.create(request, "checkout-1", authenticatedUser);
        var retried = controller.create(request, "checkout-1", authenticatedUser);

        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        assertEquals(HttpStatus.OK, retried.getStatusCode());
        verify(stock, times(1)).reserve(any(CustomerOrder.class));
        verify(products, times(1)).findById(7L);
    }

    @Test
    void rejectsReusingAnIdempotencyKeyWithAnotherPayload() {
        Product product = product(7L, "Keyboard", "125.50");
        AtomicReference<CustomerOrder> saved = new AtomicReference<>();
        when(products.findById(7L)).thenReturn(Optional.of(product));
        when(orders.findByUserIdAndIdempotencyKey(1L, "checkout-1"))
                .thenAnswer(invocation -> Optional.ofNullable(saved.get()));
        when(orders.save(any(CustomerOrder.class))).thenAnswer(invocation -> {
            saved.set(invocation.getArgument(0));
            return saved.get();
        });
        controller.create(
                new CreateOrderRequest(List.of(new CreateOrderRequest.Item(7L, 1))),
                "checkout-1",
                authenticatedUser);

        assertThrows(DuplicateResourceException.class, () -> controller.create(
                new CreateOrderRequest(List.of(new CreateOrderRequest.Item(7L, 2))),
                "checkout-1",
                authenticatedUser));
    }

    private Product product(Long id, String name, String price) {
        Product product = Mockito.mock(Product.class);
        when(product.getId()).thenReturn(id);
        when(product.isActive()).thenReturn(true);
        when(product.getName()).thenReturn(name);
        when(product.getPrice()).thenReturn(new BigDecimal(price));
        return product;
    }
}
