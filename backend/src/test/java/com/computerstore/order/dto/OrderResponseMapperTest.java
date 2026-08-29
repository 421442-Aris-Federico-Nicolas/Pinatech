package com.computerstore.order.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.computerstore.catalog.domain.Product;
import com.computerstore.catalog.domain.ProductVariant;
import com.computerstore.order.domain.CustomerOrder;
import com.computerstore.order.domain.FulfillmentMethod;
import com.computerstore.order.domain.OrderItem;
import com.computerstore.order.domain.PickupLocationSnapshot;
import com.computerstore.user.domain.UserAccount;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OrderResponseMapperTest {

    @Test
    void exposesPickupSnapshotAndKeepsLegacyOrdersReadable() {
        UserAccount user = user();
        CustomerOrder current = new CustomerOrder(
                user,
                List.of(item()),
                BigDecimal.TEN,
                Instant.now().plusSeconds(300),
                null,
                null,
                FulfillmentMethod.PICKUP,
                new PickupLocationSnapshot(
                        "CORDOBA-CENTRO", "Pinatech Cordoba", List.of("Street 123", "Local 4"),
                        "Cordoba", "X", "5000", "Bring your ID.", "Monday to Friday"));
        CustomerOrder legacy = new CustomerOrder(user, List.of(item()), BigDecimal.TEN);

        OrderResponse response = OrderResponseMapper.toResponse(current);

        assertEquals("PICKUP", response.fulfillmentMethod());
        assertEquals(BigDecimal.ZERO, response.paymentDiscount());
        assertEquals("CORDOBA-CENTRO", response.pickupLocation().code());
        assertEquals(List.of("Street 123", "Local 4"), response.pickupLocation().addressLines());
        assertNull(OrderResponseMapper.toResponse(legacy).fulfillmentMethod());
        assertNull(OrderResponseMapper.toResponse(legacy).pickupLocation());
    }

    private UserAccount user() {
        UserAccount user = Mockito.mock(UserAccount.class);
        when(user.getFirstName()).thenReturn("Customer");
        when(user.getLastName()).thenReturn("Example");
        when(user.getEmail()).thenReturn("customer@example.com");
        return user;
    }

    private OrderItem item() {
        Product product = Mockito.mock(Product.class);
        when(product.getId()).thenReturn(1L);
        when(product.getName()).thenReturn("Keyboard");
        when(product.getPrice()).thenReturn(BigDecimal.TEN);
        ProductVariant variant = Mockito.mock(ProductVariant.class);
        when(variant.getId()).thenReturn(2L);
        when(variant.getProduct()).thenReturn(product);
        when(variant.getColorName()).thenReturn("Black");
        return new OrderItem(variant, 1);
    }
}
