package com.computerstore.email;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.computerstore.order.domain.CustomerOrder;
import com.computerstore.order.domain.FulfillmentMethod;
import com.computerstore.order.domain.FulfillmentStatus;
import com.computerstore.order.domain.OrderItem;
import com.computerstore.order.domain.OrderStatus;
import com.computerstore.order.domain.PaymentMethod;
import com.computerstore.order.domain.PaymentStatus;
import com.computerstore.order.domain.PickupLocationSnapshot;
import com.computerstore.user.domain.UserAccount;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OrderEmailOutboxServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-29T10:00:00Z");
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    @Test
    void classifiesAnExceptionWithoutAMessageAsFailure() {
        EmailOutboxRepository entries = mock(EmailOutboxRepository.class);
        TransactionalEmailService email = mock(TransactionalEmailService.class);
        EmailOutboxCompletionService completion = mock(EmailOutboxCompletionService.class);
        OrderEmailOutboxService service = new OrderEmailOutboxService(
                entries, email, completion, Clock.fixed(NOW, ZoneOffset.UTC), JSON, "");
        UUID id = UUID.randomUUID();
        UUID leaseToken = UUID.randomUUID();
        var instruction = new OrderEmailOutboxService.Instruction(
                id, leaseToken, OrderEmailEventType.ORDER_CREATED, 41L,
                "customer@example.com", "Ada", null, null);
        RuntimeException providerFailure = new RuntimeException();
        org.mockito.Mockito.doThrow(providerFailure).when(email).sendOrderEvent(
                id, "customer@example.com", "Ada", OrderEmailEventType.ORDER_CREATED, 41L, null);

        service.deliver(instruction);

        verify(completion).failure(id, leaseToken, providerFailure);
    }

    @Test
    void enqueuesIndependentCustomerAndSellerEventsWithSerializedSnapshot() throws Exception {
        EmailOutboxRepository entries = mock(EmailOutboxRepository.class);
        OrderEmailOutboxService service = new OrderEmailOutboxService(
                entries, mock(TransactionalEmailService.class), mock(EmailOutboxCompletionService.class),
                Clock.fixed(NOW, ZoneOffset.UTC), JSON, " sales@example.com ");
        CustomerOrder order = order();

        service.enqueue(order, OrderEmailEventType.ORDER_CREATED);

        ArgumentCaptor<EmailOutboxEntry> saved = ArgumentCaptor.forClass(EmailOutboxEntry.class);
        verify(entries, times(2)).save(saved.capture());
        assertEquals(OrderEmailEventType.ORDER_CREATED, saved.getAllValues().get(0).getEventType());
        EmailOutboxEntry seller = saved.getAllValues().get(1);
        assertEquals(OrderEmailEventType.SELLER_ORDER_CREATED, seller.getEventType());
        assertEquals("sales@example.com", seller.getRecipient());
        SellerOrderSnapshot snapshot = JSON.readValue(seller.getSellerPayload(), SellerOrderSnapshot.class);
        assertEquals(41L, snapshot.orderId());
        assertEquals(NOW, snapshot.eventDate());
        assertEquals("PENDING_PAYMENT", snapshot.orderStatus());
        assertEquals("PENDING", snapshot.paymentStatus());
        assertEquals("Ada Lovelace", snapshot.customerName());
        assertEquals("PICKUP", snapshot.fulfillmentMethod());
        assertEquals(List.of("Street 123", "Local 4"), snapshot.pickup().addressLines());
        assertEquals("Keyboard <Pro>", snapshot.items().getFirst().product());
        assertEquals(new BigDecimal("100.00"), snapshot.items().getFirst().subtotal());
    }

    @Test
    void emptySellerRecipientLeavesCustomerEnqueueUnchanged() {
        EmailOutboxRepository entries = mock(EmailOutboxRepository.class);
        OrderEmailOutboxService service = new OrderEmailOutboxService(
                entries, mock(TransactionalEmailService.class), mock(EmailOutboxCompletionService.class),
                Clock.fixed(NOW, ZoneOffset.UTC), JSON, "  ");

        service.enqueue(order(), OrderEmailEventType.PAYMENT_APPROVED);

        verify(entries).save(any(EmailOutboxEntry.class));
    }

    @Test
    void shipmentTrackingUsesAnImmutablePayloadSnapshot() throws Exception {
        EmailOutboxRepository entries = mock(EmailOutboxRepository.class);
        OrderEmailOutboxService service = new OrderEmailOutboxService(entries, mock(TransactionalEmailService.class),
                mock(EmailOutboxCompletionService.class), Clock.fixed(NOW, ZoneOffset.UTC), JSON, "");
        var snapshot = new ShipmentTrackingSnapshot("Andreani", "TRACK-1",
                Instant.parse("2026-09-05T20:00:00Z"), "https://tracking.example/TRACK-1");

        service.enqueueTracking(order(), snapshot);

        ArgumentCaptor<EmailOutboxEntry> saved = ArgumentCaptor.forClass(EmailOutboxEntry.class);
        verify(entries).save(saved.capture());
        assertEquals(OrderEmailEventType.SHIPMENT_TRACKING_AVAILABLE, saved.getValue().getEventType());
        assertEquals(snapshot, JSON.readValue(saved.getValue().getEventPayload(), ShipmentTrackingSnapshot.class));
    }

    @Test
    void paymentApprovalUsesItsOwnSellerEventAndApprovedSnapshot() throws Exception {
        EmailOutboxRepository entries = mock(EmailOutboxRepository.class);
        OrderEmailOutboxService service = new OrderEmailOutboxService(
                entries, mock(TransactionalEmailService.class), mock(EmailOutboxCompletionService.class),
                Clock.fixed(NOW, ZoneOffset.UTC), JSON, "sales@example.com");
        CustomerOrder order = order();
        when(order.getPaymentStatus()).thenReturn(PaymentStatus.APPROVED);

        service.enqueue(order, OrderEmailEventType.PAYMENT_APPROVED);

        ArgumentCaptor<EmailOutboxEntry> saved = ArgumentCaptor.forClass(EmailOutboxEntry.class);
        verify(entries, times(2)).save(saved.capture());
        EmailOutboxEntry seller = saved.getAllValues().get(1);
        assertEquals(OrderEmailEventType.SELLER_PAYMENT_APPROVED, seller.getEventType());
        assertEquals("APPROVED", JSON.readValue(seller.getSellerPayload(), SellerOrderSnapshot.class).paymentStatus());
    }

    @Test
    void invalidConfiguredSellerRecipientFailsClearly() {
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> new OrderEmailOutboxService(
                mock(EmailOutboxRepository.class), mock(TransactionalEmailService.class),
                mock(EmailOutboxCompletionService.class), Clock.fixed(NOW, ZoneOffset.UTC), JSON,
                "first@example.com,second@example.com"));

        assertEquals("app.email.seller-recipient must be a valid single email address.", failure.getMessage());
    }

    @Test
    void deserializesSellerSnapshotAtDelivery() throws Exception {
        TransactionalEmailService email = mock(TransactionalEmailService.class);
        EmailOutboxCompletionService completion = mock(EmailOutboxCompletionService.class);
        OrderEmailOutboxService service = new OrderEmailOutboxService(
                mock(EmailOutboxRepository.class), email, completion, Clock.fixed(NOW, ZoneOffset.UTC), JSON,
                "sales@example.com");
        UUID id = UUID.randomUUID();
        UUID leaseToken = UUID.randomUUID();
        SellerOrderSnapshot snapshot = SellerOrderSnapshot.from(order(), NOW);
        var instruction = new OrderEmailOutboxService.Instruction(
                id, leaseToken, OrderEmailEventType.SELLER_PAYMENT_APPROVED, 41L,
                "sales@example.com", "Ada", null, JSON.writeValueAsString(snapshot));

        service.deliver(instruction);

        verify(email).sendSellerOrderEvent(id, "sales@example.com",
                OrderEmailEventType.SELLER_PAYMENT_APPROVED, snapshot);
        verify(completion).success(id, leaseToken);
    }

    @Test
    void rejectsSellerEventWithoutItsSnapshot() {
        TransactionalEmailService email = mock(TransactionalEmailService.class);
        EmailOutboxCompletionService completion = mock(EmailOutboxCompletionService.class);
        OrderEmailOutboxService service = new OrderEmailOutboxService(
                mock(EmailOutboxRepository.class), email, completion, Clock.fixed(NOW, ZoneOffset.UTC), JSON,
                "sales@example.com");
        UUID id = UUID.randomUUID();
        UUID leaseToken = UUID.randomUUID();
        var instruction = new OrderEmailOutboxService.Instruction(
                id, leaseToken, OrderEmailEventType.SELLER_ORDER_CREATED, 41L,
                "sales@example.com", "Ada", null, null);

        service.deliver(instruction);

        verify(completion).failure(org.mockito.ArgumentMatchers.eq(id), org.mockito.ArgumentMatchers.eq(leaseToken),
                org.mockito.ArgumentMatchers.argThat(error -> error instanceof IllegalStateException
                        && "Seller order email snapshot is missing.".equals(error.getMessage())));
        verify(email, org.mockito.Mockito.never()).sendOrderEvent(any(), any(), any(), any(), any(), any());
        verify(email, org.mockito.Mockito.never()).sendSellerOrderEvent(any(), any(), any(), any());
    }

    @Test
    void completionIgnoresAStaleLeaseToken() {
        EmailOutboxRepository entries = mock(EmailOutboxRepository.class);
        EmailOutboxEntry entry = mock(EmailOutboxEntry.class);
        UUID id = UUID.randomUUID();
        UUID staleToken = UUID.randomUUID();
        when(entries.findByIdForUpdate(id)).thenReturn(Optional.of(entry));
        when(entry.hasLease(staleToken)).thenReturn(false);
        EmailOutboxCompletionService completion = new EmailOutboxCompletionService(
                entries, Clock.fixed(NOW, ZoneOffset.UTC));

        completion.success(id, staleToken);

        verify(entry, org.mockito.Mockito.never()).sent(NOW);
    }

    private static CustomerOrder order() {
        CustomerOrder order = mock(CustomerOrder.class);
        UserAccount user = mock(UserAccount.class);
        when(order.getId()).thenReturn(41L);
        when(order.getCreatedAt()).thenReturn(NOW);
        when(order.getUser()).thenReturn(user);
        when(user.getFirstName()).thenReturn("Ada");
        when(user.getLastName()).thenReturn("Lovelace");
        when(user.getEmail()).thenReturn("ada@example.com");
        when(user.getPhone()).thenReturn("3515550101");
        when(order.getPaymentMethod()).thenReturn(PaymentMethod.MERCADO_PAGO);
        when(order.getStatus()).thenReturn(OrderStatus.PENDING_PAYMENT);
        when(order.getPaymentStatus()).thenReturn(PaymentStatus.PENDING);
        when(order.getFulfillmentStatus()).thenReturn(FulfillmentStatus.PENDING);
        when(order.getCurrency()).thenReturn("ARS");
        when(order.getSubtotal()).thenReturn(new BigDecimal("100.00"));
        when(order.getPaymentDiscount()).thenReturn(BigDecimal.ZERO);
        when(order.getPaymentSurcharge()).thenReturn(BigDecimal.ZERO);
        when(order.getTotal()).thenReturn(new BigDecimal("100.00"));
        when(order.getFulfillmentMethod()).thenReturn(FulfillmentMethod.PICKUP);
        when(order.getPickupLocation()).thenReturn(new PickupLocationSnapshot(
                "CENTRO", "Pinatech Centro", List.of("Street 123", "Local 4"),
                "Cordoba", "X", "5000", "Bring ID", "Mon-Fri"));
        OrderItem item = mock(OrderItem.class);
        when(item.getProductName()).thenReturn("Keyboard <Pro>");
        when(item.getVariantColorName()).thenReturn("Black");
        when(item.getVariantColorHex()).thenReturn("#000000");
        when(item.getQuantity()).thenReturn(1);
        when(item.getUnitPrice()).thenReturn(new BigDecimal("100.00"));
        when(item.getSubtotal()).thenReturn(new BigDecimal("100.00"));
        when(order.getItems()).thenReturn(List.of(item));
        return order;
    }
}
