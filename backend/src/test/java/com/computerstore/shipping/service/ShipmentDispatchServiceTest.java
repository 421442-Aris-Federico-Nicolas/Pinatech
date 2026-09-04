package com.computerstore.shipping.service;

import static org.junit.jupiter.api.Assertions.*;
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
import com.computerstore.order.domain.FulfillmentMethod;
import com.computerstore.shipping.domain.OrderShipmentStatus;
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

    @Test
    void providerCancellationUpdatesFulfillmentWithoutTouchingStockOrEmail() {
        Instant now = Instant.parse("2026-09-04T12:00:00Z");
        CustomerOrder order = mock(CustomerOrder.class);
        when(order.getId()).thenReturn(42L);
        when(order.getPaymentStatus()).thenReturn(PaymentStatus.APPROVED);
        when(order.getStatus()).thenReturn(OrderStatus.PAID);
        OrderShipment shipment = new OrderShipment(order, "pinatech", now);
        var token = shipment.lease(now);
        shipment.created(provider("new", now), token, now);
        OrderShipmentRepository shipments = mock(OrderShipmentRepository.class);
        when(shipments.findByProviderShipmentId(99L)).thenReturn(Optional.of(shipment));
        when(shipments.findByIdForUpdate(shipment.getId())).thenReturn(Optional.of(shipment));
        CustomerOrderRepository orders = mock(CustomerOrderRepository.class);
        when(orders.findByIdForUpdate(42L)).thenReturn(Optional.of(order));
        ShipmentEventRepository events = mock(ShipmentEventRepository.class);
        OrderEmailOutboxService outbox = mock(OrderEmailOutboxService.class);
        OrderStockService stock = mock(OrderStockService.class);
        ShipmentDispatchService service = new ShipmentDispatchService(shipments, events, orders, outbox, stock,
                properties(), Clock.fixed(now.plusSeconds(1), ZoneOffset.UTC));

        service.applyWebhook(99L, provider("canceled", now.plusSeconds(1)), List.of());

        assertEquals(OrderShipmentStatus.CANCELLED, shipment.getStatus());
        verify(order).markShipmentCancelled();
        verify(events).save(any());
        verifyNoInteractions(stock, outbox);
    }

    @Test
    void cancelledShipmentRetryQueuesAReplacementWithANewExternalId() {
        Instant now = Instant.parse("2026-09-04T12:00:00Z");
        CustomerOrder order = mock(CustomerOrder.class);
        when(order.getId()).thenReturn(42L);
        when(order.getFulfillmentMethod()).thenReturn(FulfillmentMethod.DELIVERY);
        when(order.getPaymentStatus()).thenReturn(PaymentStatus.APPROVED);
        when(order.getStatus()).thenReturn(OrderStatus.PAID);
        OrderShipment shipment = new OrderShipment(order, "pinatech", now);
        var token = shipment.lease(now);
        shipment.created(provider("new", now), token, now);
        shipment.cancelled(now);
        String cancelledExternalId = shipment.getExternalId();
        OrderShipmentRepository shipments = mock(OrderShipmentRepository.class);
        when(shipments.findByOrderId(42L)).thenReturn(Optional.of(shipment));
        when(shipments.findByIdForUpdate(shipment.getId())).thenReturn(Optional.of(shipment));
        CustomerOrderRepository orders = mock(CustomerOrderRepository.class);
        when(orders.findByIdForUpdate(42L)).thenReturn(Optional.of(order));
        ShipmentDispatchService service = new ShipmentDispatchService(shipments, mock(ShipmentEventRepository.class),
                orders, mock(OrderEmailOutboxService.class), mock(OrderStockService.class), properties(),
                Clock.fixed(now.plusSeconds(1), ZoneOffset.UTC));

        service.retry(42L);

        assertEquals(OrderShipmentStatus.PENDING_CREATE, shipment.getStatus());
        assertNull(shipment.getProviderShipmentId());
        assertNotEquals(cancelledExternalId, shipment.getExternalId());
        verify(order).markShipmentReplacementPending();
    }

    @Test
    void staleWebhookCannotReattachTheShipmentReplacedWhileWaitingForItsLock() {
        Instant now = Instant.parse("2026-09-04T12:00:00Z");
        CustomerOrder order = mock(CustomerOrder.class);
        when(order.getId()).thenReturn(42L);
        OrderShipment shipment = new OrderShipment(order, "pinatech", now);
        var token = shipment.lease(now);
        shipment.created(provider("new", now), token, now);
        shipment.cancelled(now);
        shipment.replaceCancelled("pinatech", now.plusSeconds(1));
        OrderShipmentRepository shipments = mock(OrderShipmentRepository.class);
        when(shipments.findByProviderShipmentId(99L)).thenReturn(Optional.of(shipment));
        when(shipments.findByIdForUpdate(shipment.getId())).thenReturn(Optional.of(shipment));
        CustomerOrderRepository orders = mock(CustomerOrderRepository.class);
        ShipmentDispatchService service = new ShipmentDispatchService(shipments, mock(ShipmentEventRepository.class),
                orders, mock(OrderEmailOutboxService.class), mock(OrderStockService.class), properties(),
                Clock.fixed(now.plusSeconds(2), ZoneOffset.UTC));

        service.applyWebhook(99L, provider("canceled", now.plusSeconds(2)), List.of());

        assertEquals(OrderShipmentStatus.PENDING_CREATE, shipment.getStatus());
        assertNull(shipment.getProviderShipmentId());
        verifyNoInteractions(orders);
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
