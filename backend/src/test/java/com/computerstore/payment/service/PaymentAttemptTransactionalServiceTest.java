package com.computerstore.payment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.computerstore.catalog.domain.Product;
import com.computerstore.catalog.domain.ProductVariant;
import com.computerstore.order.domain.CustomerOrder;
import com.computerstore.order.domain.OrderItem;
import com.computerstore.order.domain.OrderStatus;
import com.computerstore.order.domain.PaymentStatus;
import com.computerstore.order.repository.CustomerOrderRepository;
import com.computerstore.order.service.OrderStockService;
import com.computerstore.payment.config.MercadoPagoEnvironment;
import com.computerstore.payment.config.MercadoPagoProperties;
import com.computerstore.payment.domain.PaymentAttempt;
import com.computerstore.payment.domain.PaymentAttemptStatus;
import com.computerstore.payment.domain.PaymentEvent;
import com.computerstore.payment.gateway.ProviderPayment;
import com.computerstore.payment.repository.PaymentAttemptRepository;
import com.computerstore.payment.repository.PaymentEventRepository;
import com.computerstore.user.domain.UserAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PaymentAttemptTransactionalServiceTest {

    private final PaymentAttemptRepository attempts = Mockito.mock(PaymentAttemptRepository.class);
    private final PaymentEventRepository events = Mockito.mock(PaymentEventRepository.class);
    private final CustomerOrderRepository orders = Mockito.mock(CustomerOrderRepository.class);
    private final OrderStockService stock = Mockito.mock(OrderStockService.class);
    private final PaymentAttemptTransactionalService service = new PaymentAttemptTransactionalService(
            attempts, events, orders, stock, properties());

    @BeforeEach
    void setUp() {
        when(events.findByEventKey(any())).thenReturn(Optional.empty());
        when(events.save(any(PaymentEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void approvedPaymentBeforeExpiryPaysTheOrder() {
        Instant expiry = Instant.now().plusSeconds(300);
        CustomerOrder order = order(expiry);
        PaymentAttempt attempt = readyAttempt(order);
        when(attempts.findByPublicIdForUpdate(attempt.getPublicId())).thenReturn(Optional.of(attempt));
        when(orders.findByIdForUpdate(null)).thenReturn(Optional.of(order));

        Optional<RefundInstruction> refund = service.processWebhook(
                payment(attempt, "approved", expiry.minusSeconds(1)),
                "123",
                "request-1",
                "{\"data\":{\"id\":\"123\"}}");

        assertTrue(refund.isEmpty());
        assertEquals(OrderStatus.PAID, order.getStatus());
        assertEquals("MERCADO_PAGO", order.getPaymentMethod());
        assertEquals(PaymentAttemptStatus.APPROVED, attempt.getStatus());
        verify(stock, never()).release(order);
    }

    @Test
    void lateApprovedPaymentCancelsReservationAndCreatesAnIdempotentRefund() {
        Instant expiry = Instant.now().minusSeconds(60);
        CustomerOrder order = order(expiry);
        PaymentAttempt attempt = readyAttempt(order);
        when(attempts.findByPublicIdForUpdate(attempt.getPublicId())).thenReturn(Optional.of(attempt));
        when(orders.findByIdForUpdate(null)).thenReturn(Optional.of(order));

        Optional<RefundInstruction> refund = service.processWebhook(
                payment(attempt, "approved", expiry.plusSeconds(1)),
                "123",
                "request-1",
                "{\"data\":{\"id\":\"123\"}}");

        assertTrue(refund.isPresent());
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals(PaymentStatus.REFUND_PENDING, order.getPaymentStatus());
        assertEquals(PaymentAttemptStatus.REFUND_PENDING, attempt.getStatus());
        assertEquals(attempt.getRefundIdempotencyKey(), refund.get().idempotencyKey());
        verify(stock).release(order);

        service.refundCompleted(refund.get(), "refund-1");

        assertEquals(PaymentStatus.REFUNDED, order.getPaymentStatus());
        assertEquals(PaymentAttemptStatus.REFUNDED, attempt.getStatus());
    }

    @Test
    void rejectedPaymentDoesNotCancelAValidReservation() {
        Instant expiry = Instant.now().plusSeconds(300);
        CustomerOrder order = order(expiry);
        PaymentAttempt attempt = readyAttempt(order);
        when(attempts.findByPublicIdForUpdate(attempt.getPublicId())).thenReturn(Optional.of(attempt));
        when(orders.findByIdForUpdate(null)).thenReturn(Optional.of(order));

        service.processWebhook(
                payment(attempt, "rejected", null),
                "123",
                "request-1",
                "{\"data\":{\"id\":\"123\"}}");

        assertEquals(OrderStatus.PENDING_PAYMENT, order.getStatus());
        assertEquals(PaymentAttemptStatus.REJECTED, attempt.getStatus());
        verify(stock, never()).release(order);
    }

    private PaymentAttempt readyAttempt(CustomerOrder order) {
        PaymentAttempt attempt = new PaymentAttempt(order, "payment-key");
        attempt.preferenceCreated("pref-1", "https://sandbox");
        return attempt;
    }

    private ProviderPayment payment(PaymentAttempt attempt, String status, Instant approvedAt) {
        return new ProviderPayment(
                "123",
                attempt.getPublicId().toString(),
                "pref-1",
                "99",
                new BigDecimal("100.00"),
                "ARS",
                status,
                status + "-detail",
                approvedAt,
                Instant.parse("2026-08-17T20:00:00Z"),
                "{\"id\":123}");
    }

    private CustomerOrder order(Instant expiry) {
        Product product = Mockito.mock(Product.class);
        when(product.getName()).thenReturn("Keyboard");
        when(product.getPrice()).thenReturn(new BigDecimal("100.00"));
        ProductVariant variant = Mockito.mock(ProductVariant.class);
        when(variant.getProduct()).thenReturn(product);
        when(variant.getColorName()).thenReturn("Black");
        return new CustomerOrder(
                new UserAccount("Customer", "Example", "customer@example.com", "hash", null),
                List.of(new OrderItem(variant, 1)),
                new BigDecimal("100.00"),
                expiry,
                null,
                null);
    }

    private MercadoPagoProperties properties() {
        return new MercadoPagoProperties(
                true,
                MercadoPagoEnvironment.SANDBOX,
                "access-token",
                "webhook-secret",
                "99",
                URI.create("https://store.example"),
                Duration.ofSeconds(1),
                Duration.ofSeconds(2));
    }
}
