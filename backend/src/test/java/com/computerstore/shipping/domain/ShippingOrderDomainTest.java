package com.computerstore.shipping.domain;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import com.computerstore.common.exception.InvalidStateTransitionException;
import com.computerstore.order.domain.*;
import com.computerstore.shipping.gateway.ZipnovaGateway;
import com.computerstore.user.domain.UserAccount;
import org.junit.jupiter.api.Test;

class ShippingOrderDomainTest {
    @Test
    void deliveryTotalIncludesFullShippingAndOnlyDiscountsProducts() {
        UserAccount user = mock(UserAccount.class);
        var option = new ZipnovaGateway.QuoteOption(3, "Andreani", "standard", "Estandar", "carrier_pickup",
                new BigDecimal("121.00"), null, List.of());
        ShippingQuote quote = new ShippingQuote(user, "a".repeat(64), "b".repeat(64), option, "[]",
                Instant.now(), Instant.now().plusSeconds(900));
        var address = new DeliveryAddressSnapshot("Ada", "12345678", "a@b.com", "3515550000", "A", "1", null,
                "Cordoba", "Córdoba", "X", "5000", "AR", null);
        CustomerOrder order = new CustomerOrder(user, List.of(), new BigDecimal("1000.00"), BigDecimal.ZERO,
                new BigDecimal("100.00"), new BigDecimal("121.00"), PaymentMethod.BANK_TRANSFER,
                Instant.now().plusSeconds(900), Instant.now().plusSeconds(900), mock(BankAccountSnapshot.class),
                null, null, FulfillmentMethod.DELIVERY, null, quote, address);
        assertEquals(new BigDecimal("1021.00"), order.getTotal());
        assertEquals(new BigDecimal("121.00"), order.getShippingCost());
    }

    @Test
    void manualDeliveryCannotSkipProviderAuthority() {
        CustomerOrder order = mockDeliveryOrder();
        order.transitionTo(OrderStatus.PAID); order.transitionTo(OrderStatus.PREPARING); order.transitionTo(OrderStatus.READY);
        assertThrows(InvalidStateTransitionException.class, () -> order.transitionTo(OrderStatus.DELIVERED));
        order.markAuthoritativelyShipped(); assertEquals(OrderStatus.SHIPPED, order.getStatus());
        assertTrue(order.markAuthoritativelyDelivered()); assertEquals(OrderStatus.DELIVERED, order.getStatus());
    }

    @Test
    void damageIncidentCannotBeOverwrittenByLaterDeliveredSnapshot() {
        CustomerOrder order = mock(CustomerOrder.class);
        when(order.getId()).thenReturn(42L);
        Instant now = Instant.parse("2026-09-04T12:00:00Z");
        OrderShipment shipment = new OrderShipment(order, "pinatech", now);
        var token = shipment.lease(now);
        shipment.created(provider("delivered_with_damage", now), token, now);

        shipment.update(provider("delivered", now.plusSeconds(1)), now.plusSeconds(1));

        assertTrue(shipment.isIncident());
        assertEquals(OrderShipmentStatus.INCIDENT, shipment.getStatus());
    }

    private ZipnovaGateway.ProviderShipment provider(String status, Instant updatedAt) {
        CustomerOrder order = mock(CustomerOrder.class); when(order.getId()).thenReturn(42L);
        String externalId = new OrderShipment(order, "pinatech", Instant.EPOCH).getExternalId();
        return new ZipnovaGateway.ProviderShipment(99L, externalId, status, null, null, null, null,
                updatedAt, "Andreani");
    }

    private CustomerOrder mockDeliveryOrder() {
        UserAccount user = mock(UserAccount.class);
        var option = new ZipnovaGateway.QuoteOption(3, "Andreani", "standard", "Estandar", "carrier_pickup",
                BigDecimal.TEN, null, List.of());
        ShippingQuote quote = new ShippingQuote(user, "a".repeat(64), "b".repeat(64), option, "[]", Instant.now(), Instant.now().plusSeconds(900));
        var address = new DeliveryAddressSnapshot("Ada", "12345678", "a@b.com", "3515550000", "A", "1", null,
                "Cordoba", "Córdoba", "X", "5000", "AR", null);
        return new CustomerOrder(user, List.of(), new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.TEN, PaymentMethod.MERCADO_PAGO, Instant.now().plusSeconds(900), null, null,
                null, null, FulfillmentMethod.DELIVERY, null, quote, address);
    }
}
