package com.computerstore.shipping.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.computerstore.email.OrderEmailEventType;
import com.computerstore.email.OrderEmailOutboxService;
import com.computerstore.order.domain.CustomerOrder;
import com.computerstore.order.domain.OrderCancellationReason;
import com.computerstore.order.domain.OrderStatus;
import com.computerstore.order.domain.PaymentMethod;
import com.computerstore.order.domain.PaymentStatus;
import com.computerstore.order.domain.FulfillmentMethod;
import com.computerstore.payment.domain.PaymentAttempt;
import com.computerstore.payment.domain.ProviderPaymentRecord;
import com.computerstore.payment.service.RefundInstruction;
import com.computerstore.shipping.domain.OrderShipmentStatus;
import com.computerstore.shipping.domain.ShipmentCancellationScope;
import com.computerstore.order.repository.CustomerOrderRepository;
import com.computerstore.order.service.OrderStockService;
import com.computerstore.shipping.config.ZipnovaProperties;
import com.computerstore.shipping.domain.OrderShipment;
import com.computerstore.shipping.gateway.ZipnovaGateway;
import com.computerstore.shipping.repository.OrderShipmentRepository;
import com.computerstore.shipping.repository.ShipmentEventRepository;
import com.computerstore.payment.repository.PaymentAttemptRepository;
import com.computerstore.payment.repository.ProviderPaymentRepository;
import jakarta.persistence.EntityManager;
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
        when(orders.findById(42L)).thenReturn(Optional.of(order));
        ShipmentDispatchService service = new ShipmentDispatchService(shipments, mock(ShipmentEventRepository.class),
                orders, mock(OrderEmailOutboxService.class), mock(OrderStockService.class), mock(ProviderPaymentRepository.class),
                mock(PaymentAttemptRepository.class), mock(EntityManager.class), properties(),
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
        when(shipments.findById(shipment.getId())).thenReturn(Optional.of(shipment));
        when(shipments.findByIdForUpdate(shipment.getId())).thenReturn(Optional.of(shipment));
        when(shipments.findByProviderShipmentId(99L)).thenReturn(Optional.of(shipment));
        CustomerOrderRepository orders = mock(CustomerOrderRepository.class);
        when(orders.findByIdForUpdate(42L)).thenReturn(Optional.of(order));
        OrderEmailOutboxService outbox = mock(OrderEmailOutboxService.class);
        OrderStockService stock = mock(OrderStockService.class);
        ShipmentDispatchService service = new ShipmentDispatchService(shipments, mock(ShipmentEventRepository.class),
                orders, outbox, stock, mock(ProviderPaymentRepository.class), mock(PaymentAttemptRepository.class),
                mock(EntityManager.class), properties(), Clock.fixed(now, ZoneOffset.UTC));

        service.creationSucceeded(shipment.getId(), creationToken, provider("delivered_with_damage", now));
        when(order.getStatus()).thenReturn(OrderStatus.SHIPPED);
        service.applyWebhook(99L, provider("delivered", now.plusSeconds(1)), List.of(), true);

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
                mock(ProviderPaymentRepository.class), mock(PaymentAttemptRepository.class), mock(EntityManager.class),
                properties(), Clock.fixed(now.plusSeconds(1), ZoneOffset.UTC));

        service.applyWebhook(99L, provider("canceled", now.plusSeconds(1)), List.of(), true);

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
                orders, mock(OrderEmailOutboxService.class), mock(OrderStockService.class), mock(ProviderPaymentRepository.class),
                mock(PaymentAttemptRepository.class), mock(EntityManager.class), properties(),
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
                orders, mock(OrderEmailOutboxService.class), mock(OrderStockService.class), mock(ProviderPaymentRepository.class),
                mock(PaymentAttemptRepository.class), mock(EntityManager.class), properties(),
                Clock.fixed(now.plusSeconds(2), ZoneOffset.UTC));

        service.applyWebhook(99L, provider("canceled", now.plusSeconds(2)), List.of(), true);

        assertEquals(OrderShipmentStatus.PENDING_CREATE, shipment.getStatus());
        assertNull(shipment.getProviderShipmentId());
        verifyNoInteractions(orders);
    }

    @Test
    void cancellingAnOrderRequestsTheMercadoPagoRefundAndReleasesReservedStock() {
        Instant now = Instant.parse("2026-09-04T12:00:00Z");
        CustomerOrder order = mock(CustomerOrder.class);
        when(order.getId()).thenReturn(42L);
        when(order.getFulfillmentMethod()).thenReturn(FulfillmentMethod.DELIVERY);
        when(order.getPaymentMethod()).thenReturn(PaymentMethod.MERCADO_PAGO);
        when(order.getPaymentStatus()).thenReturn(PaymentStatus.APPROVED);
        when(order.getStatus()).thenReturn(OrderStatus.PAID);
        when(order.cancelForRefund(OrderCancellationReason.CUSTOMER_REQUEST, "Duplicate", 7L, now)).thenReturn(true);
        OrderShipment shipment = activeShipment(order, now);
        OrderShipmentRepository shipments = mock(OrderShipmentRepository.class);
        when(shipments.findByOrderId(42L)).thenReturn(Optional.of(shipment));
        when(shipments.findByIdForUpdate(shipment.getId())).thenReturn(Optional.of(shipment));
        CustomerOrderRepository orders = mock(CustomerOrderRepository.class);
        when(orders.findById(42L)).thenReturn(Optional.of(order));
        when(orders.findByIdForUpdate(42L)).thenReturn(Optional.of(order));
        ProviderPaymentRecord payment = mock(ProviderPaymentRecord.class);
        PaymentAttempt attempt = mock(PaymentAttempt.class);
        UUID attemptId = UUID.randomUUID();
        UUID refundKey = UUID.randomUUID();
        when(payment.getAttempt()).thenReturn(attempt);
        when(payment.getProviderPaymentId()).thenReturn("payment-123");
        when(payment.requestRefund(now)).thenReturn(refundKey);
        when(attempt.getPublicId()).thenReturn(attemptId);
        ProviderPaymentRepository providerPayments = mock(ProviderPaymentRepository.class);
        when(providerPayments.findFundsPaymentByOrderIdForUpdate(42L)).thenReturn(Optional.of(payment));
        PaymentAttemptRepository paymentAttempts = mock(PaymentAttemptRepository.class);
        when(paymentAttempts.findFundingAttemptByOrderIdForUpdate(42L)).thenReturn(Optional.of(attempt));
        OrderEmailOutboxService outbox = mock(OrderEmailOutboxService.class);
        OrderStockService stock = mock(OrderStockService.class);
        ShipmentDispatchService service = new ShipmentDispatchService(shipments, mock(ShipmentEventRepository.class),
                orders, outbox, stock, providerPayments, paymentAttempts, mock(EntityManager.class), properties(),
                Clock.fixed(now, ZoneOffset.UTC));

        var cancellation = service.requestCancellation(42L, ShipmentCancellationScope.ORDER,
                OrderCancellationReason.CUSTOMER_REQUEST, "Duplicate", 7L);
        Optional<RefundInstruction> refund = service.cancellationSucceeded(42L, cancellation.providerId());

        assertTrue(cancellation.providerCancellationRequired());
        assertEquals(refundKey, refund.orElseThrow().idempotencyKey());
        assertEquals("payment-123", refund.orElseThrow().paymentId());
        verify(stock).release(order);
        verify(outbox).enqueue(order, OrderEmailEventType.ORDER_CANCELLED, "Solicitud del cliente");
        verify(attempt).summaryStatus(null, com.computerstore.payment.domain.PaymentAttemptStatus.REFUND_PENDING);
    }

    @Test
    void ineligibleMercadoPagoCancellationFailsBeforeLookingUpTheFundingPayment() {
        Instant now = Instant.parse("2026-09-04T12:00:00Z");
        CustomerOrder order = mock(CustomerOrder.class);
        when(order.getId()).thenReturn(42L);
        when(order.getFulfillmentMethod()).thenReturn(FulfillmentMethod.DELIVERY);
        when(order.getPaymentMethod()).thenReturn(PaymentMethod.MERCADO_PAGO);
        when(order.getPaymentStatus()).thenReturn(PaymentStatus.PENDING);
        when(order.getStatus()).thenReturn(OrderStatus.PAID);
        OrderShipment shipment = activeShipment(order, now);
        OrderShipmentRepository shipments = mock(OrderShipmentRepository.class);
        when(shipments.findByOrderId(42L)).thenReturn(Optional.of(shipment));
        CustomerOrderRepository orders = mock(CustomerOrderRepository.class);
        when(orders.findById(42L)).thenReturn(Optional.of(order));
        ProviderPaymentRepository providerPayments = mock(ProviderPaymentRepository.class);
        PaymentAttemptRepository paymentAttempts = mock(PaymentAttemptRepository.class);
        ShipmentDispatchService service = new ShipmentDispatchService(shipments, mock(ShipmentEventRepository.class),
                orders, mock(OrderEmailOutboxService.class), mock(OrderStockService.class), providerPayments,
                paymentAttempts, mock(EntityManager.class), properties(), Clock.fixed(now, ZoneOffset.UTC));

        assertThrows(com.computerstore.common.exception.InvalidStateTransitionException.class,
                () -> service.requestCancellation(42L, ShipmentCancellationScope.ORDER,
                        OrderCancellationReason.CUSTOMER_REQUEST, null, 7L));

        verifyNoInteractions(providerPayments, paymentAttempts);
    }

    @Test
    void matchingPendingCancellationIsIdempotentAndConflictingRequestIsRejected() {
        Instant now = Instant.parse("2026-09-04T12:00:00Z");
        CustomerOrder order = mock(CustomerOrder.class);
        when(order.getId()).thenReturn(42L);
        when(order.getFulfillmentMethod()).thenReturn(FulfillmentMethod.DELIVERY);
        when(order.getPaymentMethod()).thenReturn(PaymentMethod.BANK_TRANSFER);
        when(order.getPaymentStatus()).thenReturn(PaymentStatus.APPROVED);
        when(order.getStatus()).thenReturn(OrderStatus.PAID);
        OrderShipment shipment = activeShipment(order, now);
        OrderShipmentRepository shipments = mock(OrderShipmentRepository.class);
        when(shipments.findByOrderId(42L)).thenReturn(Optional.of(shipment));
        when(shipments.findByIdForUpdate(shipment.getId())).thenReturn(Optional.of(shipment));
        CustomerOrderRepository orders = mock(CustomerOrderRepository.class);
        when(orders.findById(42L)).thenReturn(Optional.of(order));
        when(orders.findByIdForUpdate(42L)).thenReturn(Optional.of(order));
        ShipmentDispatchService service = new ShipmentDispatchService(shipments, mock(ShipmentEventRepository.class),
                orders, mock(OrderEmailOutboxService.class), mock(OrderStockService.class),
                mock(ProviderPaymentRepository.class), mock(PaymentAttemptRepository.class), mock(EntityManager.class),
                properties(), Clock.fixed(now, ZoneOffset.UTC));

        var first = service.requestCancellation(42L, ShipmentCancellationScope.ORDER,
                OrderCancellationReason.CUSTOMER_REQUEST, "Requested", 7L);
        var repeated = service.requestCancellation(42L, ShipmentCancellationScope.ORDER,
                OrderCancellationReason.CUSTOMER_REQUEST, "Requested", 7L);

        assertTrue(first.providerCancellationRequired());
        assertTrue(first.finalizationRequired());
        assertFalse(repeated.providerCancellationRequired());
        assertFalse(repeated.finalizationRequired());
        shipment.cancelled(now.plusSeconds(1));
        var localRecovery = service.requestCancellation(42L, ShipmentCancellationScope.ORDER,
                OrderCancellationReason.CUSTOMER_REQUEST, "Requested", 7L);
        assertFalse(localRecovery.providerCancellationRequired());
        assertTrue(localRecovery.finalizationRequired());
        assertThrows(com.computerstore.common.exception.InvalidStateTransitionException.class,
                () -> service.requestCancellation(42L, ShipmentCancellationScope.ORDER,
                        OrderCancellationReason.LOGISTICS_PROBLEM, "Requested", 8L));
    }

    @Test
    void cancellingOnlyTheShipmentDoesNotTouchTheOrderPaymentOrStock() {
        Instant now = Instant.parse("2026-09-04T12:00:00Z");
        CustomerOrder order = mock(CustomerOrder.class);
        when(order.getId()).thenReturn(42L);
        when(order.getFulfillmentMethod()).thenReturn(FulfillmentMethod.DELIVERY);
        when(order.getPaymentStatus()).thenReturn(PaymentStatus.APPROVED);
        when(order.getStatus()).thenReturn(OrderStatus.PAID);
        OrderShipment shipment = activeShipment(order, now);
        OrderShipmentRepository shipments = mock(OrderShipmentRepository.class);
        when(shipments.findByOrderId(42L)).thenReturn(Optional.of(shipment));
        when(shipments.findByIdForUpdate(shipment.getId())).thenReturn(Optional.of(shipment));
        CustomerOrderRepository orders = mock(CustomerOrderRepository.class);
        when(orders.findById(42L)).thenReturn(Optional.of(order));
        when(orders.findByIdForUpdate(42L)).thenReturn(Optional.of(order));
        ProviderPaymentRepository providerPayments = mock(ProviderPaymentRepository.class);
        OrderEmailOutboxService outbox = mock(OrderEmailOutboxService.class);
        OrderStockService stock = mock(OrderStockService.class);
        ShipmentDispatchService service = new ShipmentDispatchService(shipments, mock(ShipmentEventRepository.class),
                orders, outbox, stock, providerPayments, mock(PaymentAttemptRepository.class), mock(EntityManager.class),
                properties(), Clock.fixed(now, ZoneOffset.UTC));

        var cancellation = service.requestCancellation(42L, ShipmentCancellationScope.SHIPMENT_ONLY,
                null, null, 7L);
        assertTrue(service.cancellationSucceeded(42L, cancellation.providerId()).isEmpty());

        verify(order).markShipmentCancelled();
        verifyNoInteractions(providerPayments, stock, outbox);
        verify(order, never()).cancelForRefund(any(), any(), any(), any());
    }

    @Test
    void externallyCancelledOrderRestoresConsumedStockWithoutCallingTheProviderAgain() {
        Instant now = Instant.parse("2026-09-04T12:00:00Z");
        CustomerOrder order = mock(CustomerOrder.class);
        when(order.getId()).thenReturn(42L);
        when(order.getFulfillmentMethod()).thenReturn(FulfillmentMethod.DELIVERY);
        when(order.getPaymentMethod()).thenReturn(PaymentMethod.BANK_TRANSFER);
        when(order.getPaymentStatus()).thenReturn(PaymentStatus.APPROVED);
        when(order.getStatus()).thenReturn(OrderStatus.PREPARING);
        when(order.cancelForRefund(OrderCancellationReason.LOGISTICS_PROBLEM, null, 7L, now)).thenReturn(true);
        OrderShipment shipment = activeShipment(order, now);
        shipment.cancelled(now);
        OrderShipmentRepository shipments = mock(OrderShipmentRepository.class);
        when(shipments.findByOrderId(42L)).thenReturn(Optional.of(shipment));
        when(shipments.findByIdForUpdate(shipment.getId())).thenReturn(Optional.of(shipment));
        CustomerOrderRepository orders = mock(CustomerOrderRepository.class);
        when(orders.findById(42L)).thenReturn(Optional.of(order));
        when(orders.findByIdForUpdate(42L)).thenReturn(Optional.of(order));
        ProviderPaymentRepository providerPayments = mock(ProviderPaymentRepository.class);
        OrderEmailOutboxService outbox = mock(OrderEmailOutboxService.class);
        OrderStockService stock = mock(OrderStockService.class);
        ShipmentDispatchService service = new ShipmentDispatchService(shipments, mock(ShipmentEventRepository.class),
                orders, outbox, stock, providerPayments, mock(PaymentAttemptRepository.class), mock(EntityManager.class),
                properties(), Clock.fixed(now, ZoneOffset.UTC));

        var cancellation = service.requestCancellation(42L, ShipmentCancellationScope.ORDER,
                OrderCancellationReason.LOGISTICS_PROBLEM, null, 7L);
        assertFalse(cancellation.providerCancellationRequired());
        assertTrue(service.cancellationSucceeded(42L, cancellation.providerId()).isEmpty());

        verify(stock).restore(order);
        verify(outbox).enqueue(order, OrderEmailEventType.ORDER_CANCELLED, "Problema logistico");
        verifyNoInteractions(providerPayments);
    }

    @Test
    void pendingCancellationPreventsAConcurrentProviderUpdateFromAdvancingTheOrder() {
        Instant now = Instant.parse("2026-09-04T12:00:00Z");
        CustomerOrder order = mock(CustomerOrder.class);
        when(order.getId()).thenReturn(42L);
        when(order.getFulfillmentMethod()).thenReturn(FulfillmentMethod.DELIVERY);
        when(order.getPaymentStatus()).thenReturn(PaymentStatus.APPROVED);
        when(order.getStatus()).thenReturn(OrderStatus.PAID);
        OrderShipment shipment = activeShipment(order, now);
        OrderShipmentRepository shipments = mock(OrderShipmentRepository.class);
        when(shipments.findByOrderId(42L)).thenReturn(Optional.of(shipment));
        when(shipments.findByProviderShipmentId(99L)).thenReturn(Optional.of(shipment));
        when(shipments.findByIdForUpdate(shipment.getId())).thenReturn(Optional.of(shipment));
        CustomerOrderRepository orders = mock(CustomerOrderRepository.class);
        when(orders.findById(42L)).thenReturn(Optional.of(order));
        when(orders.findByIdForUpdate(42L)).thenReturn(Optional.of(order));
        OrderStockService stock = mock(OrderStockService.class);
        ShipmentDispatchService service = new ShipmentDispatchService(shipments, mock(ShipmentEventRepository.class),
                orders, mock(OrderEmailOutboxService.class), stock, mock(ProviderPaymentRepository.class),
                mock(PaymentAttemptRepository.class), mock(EntityManager.class),
                properties(), Clock.fixed(now.plusSeconds(2), ZoneOffset.UTC));
        service.requestCancellation(42L, ShipmentCancellationScope.SHIPMENT_ONLY, null, null, 7L);

        service.applyWebhook(99L, provider("shipped", now.plusSeconds(1)), List.of(), true);

        assertEquals(OrderShipmentStatus.INCIDENT, shipment.getStatus());
        verifyNoInteractions(stock);
        verify(order, never()).transitionTo(any());
        verify(order, never()).markAuthoritativelyShipped();
    }

    private OrderShipment activeShipment(CustomerOrder order, Instant now) {
        OrderShipment shipment = new OrderShipment(order, "pinatech", now);
        UUID token = shipment.lease(now);
        shipment.created(provider("new", now), token, now);
        return shipment;
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
