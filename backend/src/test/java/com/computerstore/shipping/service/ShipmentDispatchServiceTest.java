package com.computerstore.shipping.service;

import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.computerstore.email.OrderEmailOutboxService;
import com.computerstore.order.domain.CustomerOrder;
import com.computerstore.order.domain.OrderStatus;
import com.computerstore.order.domain.PaymentStatus;
import com.computerstore.order.repository.CustomerOrderRepository;
import com.computerstore.order.service.OrderStockService;
import com.computerstore.shipping.config.ZipnovaProperties;
import com.computerstore.shipping.domain.OrderShipment;
import com.computerstore.shipping.gateway.ZipnovaGateway;
import com.computerstore.shipping.repository.OrderShipmentRepository;
import com.computerstore.shipping.repository.ShipmentEventRepository;
import org.junit.jupiter.api.Test;

class ShipmentDispatchServiceTest {
    @Test
    void pausesCreationWhenPaymentIsNoLongerApproved() {
        Instant now = Instant.parse("2026-09-04T12:00:00Z");
        CustomerOrder order = mock(CustomerOrder.class);
        when(order.getId()).thenReturn(42L);
        when(order.getPaymentStatus()).thenReturn(PaymentStatus.REFUNDED);
        OrderShipment shipment = new OrderShipment(order, "pinatech", now);
        OrderShipmentRepository shipments = mock(OrderShipmentRepository.class);
        when(shipments.findNextCreationForUpdate(now)).thenReturn(Optional.of(shipment.getId()));
        when(shipments.findByIdForUpdate(shipment.getId())).thenReturn(Optional.of(shipment));
        CustomerOrderRepository orders = mock(CustomerOrderRepository.class);
        when(orders.findByIdForUpdate(42L)).thenReturn(Optional.of(order));
        ShipmentDispatchService service = new ShipmentDispatchService(shipments, mock(ShipmentEventRepository.class),
                orders, mock(OrderEmailOutboxService.class), mock(OrderStockService.class), properties(),
                Clock.fixed(now, ZoneOffset.UTC));

        org.junit.jupiter.api.Assertions.assertTrue(service.claimCreation().isEmpty());
        org.junit.jupiter.api.Assertions.assertEquals(
                com.computerstore.shipping.domain.OrderShipmentStatus.BLOCKED_PAYMENT, shipment.getStatus());
    }

    @Test
    void priorDamageIncidentNeverTriggersHappyDeliveryFlow() {
        Instant now = Instant.parse("2026-09-04T12:00:00Z");
        CustomerOrder order = mock(CustomerOrder.class);
        when(order.getId()).thenReturn(42L);
        when(order.getPaymentStatus()).thenReturn(PaymentStatus.APPROVED);
        when(order.getStatus()).thenReturn(OrderStatus.PAID);
        OrderShipment shipment = new OrderShipment(order, "pinatech", now);
        var creationToken = shipment.lease(now);

        OrderShipmentRepository shipments = mock(OrderShipmentRepository.class);
        when(shipments.findByIdForUpdate(shipment.getId())).thenReturn(Optional.of(shipment));
        when(shipments.findByProviderShipmentId(99L)).thenReturn(Optional.of(shipment));
        CustomerOrderRepository orders = mock(CustomerOrderRepository.class);
        when(orders.findByIdForUpdate(42L)).thenReturn(Optional.of(order));
        OrderEmailOutboxService outbox = mock(OrderEmailOutboxService.class);
        OrderStockService stock = mock(OrderStockService.class);
        ShipmentDispatchService service = new ShipmentDispatchService(shipments, mock(ShipmentEventRepository.class),
                orders, outbox, stock, properties(), Clock.fixed(now, ZoneOffset.UTC));

        service.creationSucceeded(shipment.getId(), creationToken, provider("delivered_with_damage", now));
        when(order.getStatus()).thenReturn(OrderStatus.SHIPPED);
        service.applyWebhook(99L, provider("delivered", now.plusSeconds(1)), List.of());

        verify(stock).consume(order);
        verify(order, times(2)).markAuthoritativelyShipped();
        verify(order, never()).markAuthoritativelyDelivered();
        verifyNoInteractions(outbox);
    }

    private ZipnovaGateway.ProviderShipment provider(String status, Instant updatedAt) {
        return new ZipnovaGateway.ProviderShipment(99L, externalId(), status, null, null, null, null,
                updatedAt, "Andreani");
    }

    private String externalId() {
        CustomerOrder order = mock(CustomerOrder.class); when(order.getId()).thenReturn(42L);
        return new OrderShipment(order, "pinatech", Instant.EPOCH).getExternalId();
    }

    private ZipnovaProperties properties() {
        return new ZipnovaProperties(false, false, null, null, null, null, "pinatech", "dynamic",
                Duration.ofMinutes(15), Duration.ofSeconds(1), Duration.ofSeconds(2), null,
                Duration.ofMinutes(10));
    }
}
