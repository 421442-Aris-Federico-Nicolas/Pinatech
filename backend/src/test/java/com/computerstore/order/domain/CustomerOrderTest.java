package com.computerstore.order.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;

import com.computerstore.catalog.domain.Product;
import com.computerstore.catalog.domain.ProductVariant;
import com.computerstore.common.exception.InvalidStateTransitionException;
import com.computerstore.user.domain.UserAccount;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CustomerOrderTest {

    @Test
    void permitsTheExpectedOrderLifecycle() {
        CustomerOrder order = order();

        assertEquals(PaymentStatus.PENDING, order.getPaymentStatus());
        assertEquals(FulfillmentStatus.PENDING, order.getFulfillmentStatus());
        assertEquals("ARS", order.getCurrency());
        order.transitionTo(OrderStatus.PAID);
        assertEquals(PaymentStatus.APPROVED, order.getPaymentStatus());
        order.transitionTo(OrderStatus.PREPARING);
        assertEquals(FulfillmentStatus.PREPARING, order.getFulfillmentStatus());
        order.transitionTo(OrderStatus.READY);
        assertEquals(FulfillmentStatus.READY, order.getFulfillmentStatus());
        order.transitionTo(OrderStatus.DELIVERED);

        assertEquals(OrderStatus.DELIVERED, order.getStatus());
        assertEquals(FulfillmentStatus.DELIVERED, order.getFulfillmentStatus());
    }

    @Test
    void rejectsInvalidStateTransitions() {
        CustomerOrder order = order();

        assertThrows(InvalidStateTransitionException.class, () -> order.transitionTo(OrderStatus.READY));
    }

    @Test
    void cancellingAnApprovedOrderKeepsTheApprovedPayment() {
        CustomerOrder order = order();
        order.transitionTo(OrderStatus.PAID);

        order.transitionTo(OrderStatus.CANCELLED);

        assertEquals(PaymentStatus.APPROVED, order.getPaymentStatus());
        assertEquals(FulfillmentStatus.CANCELLED, order.getFulfillmentStatus());
    }

    @Test
    void permitsCancellationWhilePreparingOrReady() {
        CustomerOrder preparing = order();
        preparing.transitionTo(OrderStatus.PAID);
        preparing.transitionTo(OrderStatus.PREPARING);

        preparing.transitionTo(OrderStatus.CANCELLED);

        assertEquals(OrderStatus.CANCELLED, preparing.getStatus());
        assertEquals(FulfillmentStatus.CANCELLED, preparing.getFulfillmentStatus());

        CustomerOrder ready = order();
        ready.transitionTo(OrderStatus.PAID);
        ready.transitionTo(OrderStatus.PREPARING);
        ready.transitionTo(OrderStatus.READY);

        ready.transitionTo(OrderStatus.CANCELLED);

        assertEquals(OrderStatus.CANCELLED, ready.getStatus());
        assertEquals(FulfillmentStatus.CANCELLED, ready.getFulfillmentStatus());
    }

    @Test
    void expirationMarksPaymentExpiredAndCancelsFulfillment() {
        CustomerOrder order = order();

        order.expire();

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals(PaymentStatus.EXPIRED, order.getPaymentStatus());
        assertEquals(FulfillmentStatus.CANCELLED, order.getFulfillmentStatus());
    }

    private CustomerOrder order() {
        Product product = Mockito.mock(Product.class);
        Mockito.when(product.getName()).thenReturn("Keyboard");
        Mockito.when(product.getPrice()).thenReturn(BigDecimal.TEN);
        ProductVariant variant = Mockito.mock(ProductVariant.class);
        Mockito.when(variant.getProduct()).thenReturn(product);
        Mockito.when(variant.getColorName()).thenReturn("Black");
        return new CustomerOrder(
                new UserAccount("Customer", "Example", "customer@example.com", "hash", null),
                List.of(new OrderItem(variant, 1)),
                BigDecimal.TEN
        );
    }
}
