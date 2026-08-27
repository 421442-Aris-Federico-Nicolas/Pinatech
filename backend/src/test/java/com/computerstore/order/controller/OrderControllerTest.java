package com.computerstore.order.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.computerstore.catalog.domain.Product;
import com.computerstore.catalog.domain.ProductVariant;
import com.computerstore.catalog.repository.ProductVariantRepository;
import com.computerstore.common.exception.DuplicateResourceException;
import com.computerstore.common.exception.EmailVerificationRequiredException;
import com.computerstore.order.config.FulfillmentProperties;
import com.computerstore.order.config.OrderProperties;
import com.computerstore.order.domain.CustomerOrder;
import com.computerstore.order.domain.FulfillmentMethod;
import com.computerstore.order.dto.CreateOrderRequest;
import com.computerstore.order.repository.CustomerOrderRepository;
import com.computerstore.order.service.OrderStockService;
import com.computerstore.order.service.FulfillmentPolicy;
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
    private final ProductVariantRepository variants = Mockito.mock(ProductVariantRepository.class);
    private final UserAccountRepository users = Mockito.mock(UserAccountRepository.class);
    private final OrderStockService stock = Mockito.mock(OrderStockService.class);
    private final UserAccount user = Mockito.mock(UserAccount.class);
    private final FulfillmentPolicy fulfillment = new FulfillmentPolicy(fulfillmentProperties());
    private final OrderController controller = new OrderController(
            orders, variants, users, stock, new OrderProperties(Duration.ofMinutes(15), 100), fulfillment);
    private final AuthenticatedUser authenticatedUser =
            new AuthenticatedUser(1L, "customer@example.com", List.of());

    @BeforeEach
    void setUp() {
        when(user.isActive()).thenReturn(true);
        when(user.isEmailVerified()).thenReturn(true);
        when(user.getFirstName()).thenReturn("Customer");
        when(user.getLastName()).thenReturn("Example");
        when(user.getEmail()).thenReturn("customer@example.com");
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
    }

    @Test
    void recalculatesTotalAndDelegatesTheStockReservation() {
        Product product = product(7L, "Keyboard", "125.50");
        ProductVariant variant = variant(7L, product);
        when(variants.findByIdAndActiveTrueAndProduct_ActiveTrue(7L)).thenReturn(Optional.of(variant));
        when(orders.save(any(CustomerOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = controller.create(
                request(2, "CORDOBA-CENTRO"),
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
        assertEquals("PICKUP", response.getBody().fulfillmentMethod());
        assertEquals("CORDOBA-CENTRO", response.getBody().pickupLocation().code());
        assertEquals(List.of("Street 123", "Local 4"), response.getBody().pickupLocation().addressLines());
    }

    @Test
    void returnsTheOriginalOrderForAnIdempotentRetryWithoutReservingAgain() {
        Product product = product(7L, "Keyboard", "125.50");
        ProductVariant variant = variant(7L, product);
        AtomicReference<CustomerOrder> saved = new AtomicReference<>();
        when(variants.findByIdAndActiveTrueAndProduct_ActiveTrue(7L)).thenReturn(Optional.of(variant));
        when(orders.findByUserIdAndIdempotencyKey(1L, "checkout-1"))
                .thenAnswer(invocation -> Optional.ofNullable(saved.get()));
        when(orders.save(any(CustomerOrder.class))).thenAnswer(invocation -> {
            saved.set(invocation.getArgument(0));
            return saved.get();
        });
        var request = request(2, "CORDOBA-CENTRO");

        var created = controller.create(request, "checkout-1", authenticatedUser);
        var retried = controller.create(request, "checkout-1", authenticatedUser);

        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        assertEquals(HttpStatus.OK, retried.getStatusCode());
        verify(stock, times(1)).reserve(any(CustomerOrder.class));
        verify(variants, times(1)).findByIdAndActiveTrueAndProduct_ActiveTrue(7L);
    }

    @Test
    void rejectsReusingAnIdempotencyKeyWithAnotherPayload() {
        Product product = product(7L, "Keyboard", "125.50");
        ProductVariant variant = variant(7L, product);
        AtomicReference<CustomerOrder> saved = new AtomicReference<>();
        when(variants.findByIdAndActiveTrueAndProduct_ActiveTrue(7L)).thenReturn(Optional.of(variant));
        when(orders.findByUserIdAndIdempotencyKey(1L, "checkout-1"))
                .thenAnswer(invocation -> Optional.ofNullable(saved.get()));
        when(orders.save(any(CustomerOrder.class))).thenAnswer(invocation -> {
            saved.set(invocation.getArgument(0));
            return saved.get();
        });
        controller.create(
                request(1, "CORDOBA-CENTRO"),
                "checkout-1",
                authenticatedUser);

        assertThrows(DuplicateResourceException.class, () -> controller.create(
                request(2, "CORDOBA-CENTRO"),
                "checkout-1",
                authenticatedUser));
    }

    @Test
    void rejectsReusingAnIdempotencyKeyWithAnotherPickupCode() {
        ProductVariant variant = variant(7L, product(7L, "Keyboard", "125.50"));
        AtomicReference<CustomerOrder> saved = new AtomicReference<>();
        when(variants.findByIdAndActiveTrueAndProduct_ActiveTrue(7L)).thenReturn(Optional.of(variant));
        when(orders.findByUserIdAndIdempotencyKey(1L, "checkout-1"))
                .thenAnswer(invocation -> Optional.ofNullable(saved.get()));
        when(orders.save(any(CustomerOrder.class))).thenAnswer(invocation -> {
            saved.set(invocation.getArgument(0));
            return saved.get();
        });
        controller.create(request(1, "CORDOBA-CENTRO"), "checkout-1", authenticatedUser);

        assertThrows(DuplicateResourceException.class, () -> controller.create(
                request(1, "CORDOBA-NORTE"), "checkout-1", authenticatedUser));
        verify(stock, times(1)).reserve(any(CustomerOrder.class));
    }

    @Test
    void requiresVerifiedEmailBeforeReadingProductsOrReservingStock() {
        when(user.isEmailVerified()).thenReturn(false);

        assertThrows(EmailVerificationRequiredException.class,
                () -> controller.create(request(1, "CORDOBA-CENTRO"), null, authenticatedUser));

        verify(variants, never()).findByIdAndActiveTrueAndProduct_ActiveTrue(any());
        verify(stock, never()).reserve(any());
    }

    private CreateOrderRequest request(int quantity, String pickupCode) {
        return new CreateOrderRequest(
                List.of(new CreateOrderRequest.Item(7L, quantity)), FulfillmentMethod.PICKUP, pickupCode,
                fulfillment.activePickupLocation().orElseThrow().version());
    }

    private static FulfillmentProperties fulfillmentProperties() {
        return new FulfillmentProperties(new FulfillmentProperties.Pickup(
                true,
                "CORDOBA-CENTRO",
                "Pinatech Cordoba",
                List.of("Street 123", "Local 4"),
                "Cordoba",
                "X",
                "5000",
                "Bring your ID.",
                "Monday to Friday 09:00-18:00"));
    }

    private Product product(Long id, String name, String price) {
        Product product = Mockito.mock(Product.class);
        when(product.getId()).thenReturn(id);
        when(product.isActive()).thenReturn(true);
        when(product.getName()).thenReturn(name);
        when(product.getPrice()).thenReturn(new BigDecimal(price));
        return product;
    }

    private ProductVariant variant(Long id, Product product) {
        ProductVariant variant = Mockito.mock(ProductVariant.class);
        when(variant.getId()).thenReturn(id);
        when(variant.getProduct()).thenReturn(product);
        when(variant.getColorName()).thenReturn("Black");
        return variant;
    }
}
