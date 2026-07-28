package com.computerstore.order.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;

import com.computerstore.catalog.domain.Product;
import com.computerstore.common.exception.InvalidStateTransitionException;
import com.computerstore.user.domain.UserAccount;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CustomerOrderTest {

    @Test
    void permitsTheExpectedOrderLifecycle() {
        CustomerOrder order = order();

        order.transitionTo(OrderStatus.PAID);
        order.transitionTo(OrderStatus.PREPARING);
        order.transitionTo(OrderStatus.READY);
        order.transitionTo(OrderStatus.DELIVERED);

        assertEquals(OrderStatus.DELIVERED, order.getStatus());
    }

    @Test
    void rejectsInvalidStateTransitions() {
        CustomerOrder order = order();

        assertThrows(InvalidStateTransitionException.class, () -> order.transitionTo(OrderStatus.READY));
    }

    private CustomerOrder order() {
        Product product = Mockito.mock(Product.class);
        Mockito.when(product.getName()).thenReturn("Keyboard");
        Mockito.when(product.getPrice()).thenReturn(BigDecimal.TEN);
        return new CustomerOrder(
                new UserAccount("Customer", "Example", "customer@example.com", "hash", null),
                List.of(new OrderItem(product, 1)),
                BigDecimal.TEN
        );
    }
}
