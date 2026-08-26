package com.computerstore.payment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.computerstore.catalog.domain.Product;
import com.computerstore.catalog.domain.ProductVariant;
import com.computerstore.common.exception.InvalidRequestException;
import com.computerstore.common.exception.BusinessRuleException;
import com.computerstore.common.exception.EmailVerificationRequiredException;
import com.computerstore.order.config.FulfillmentProperties;
import com.computerstore.order.domain.CustomerOrder;
import com.computerstore.order.domain.FulfillmentMethod;
import com.computerstore.order.domain.OrderItem;
import com.computerstore.order.domain.OrderStatus;
import com.computerstore.order.domain.PaymentStatus;
import com.computerstore.order.domain.PickupLocationSnapshot;
import com.computerstore.order.repository.CustomerOrderRepository;
import com.computerstore.order.service.OrderStockService;
import com.computerstore.order.service.FulfillmentPolicy;
import com.computerstore.payment.config.MercadoPagoEnvironment;
import com.computerstore.payment.config.MercadoPagoProperties;
import com.computerstore.payment.domain.PaymentAttempt;
import com.computerstore.payment.domain.PaymentAttemptStatus;
import com.computerstore.payment.domain.PaymentEvent;
import com.computerstore.payment.domain.ProviderPaymentRecord;
import com.computerstore.payment.gateway.ProviderPayment;
import com.computerstore.payment.gateway.RefundResult;
import com.computerstore.payment.repository.PaymentAttemptRepository;
import com.computerstore.payment.repository.PaymentEventRepository;
import com.computerstore.payment.repository.ProviderPaymentRepository;
import com.computerstore.user.domain.UserAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PaymentAttemptTransactionalServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-18T00:00:00Z");
    private final PaymentAttemptRepository attempts = Mockito.mock(PaymentAttemptRepository.class);
    private final ProviderPaymentRepository providerPayments = Mockito.mock(ProviderPaymentRepository.class);
    private final PaymentEventRepository events = Mockito.mock(PaymentEventRepository.class);
    private final CustomerOrderRepository orders = Mockito.mock(CustomerOrderRepository.class);
    private final OrderStockService stock = Mockito.mock(OrderStockService.class);
    private final Map<String, ProviderPaymentRecord> records = new HashMap<>();
    private final FulfillmentPolicy fulfillment = new FulfillmentPolicy(fulfillmentProperties());
    private final PaymentAttemptTransactionalService service = new PaymentAttemptTransactionalService(
            attempts, providerPayments, events, orders, stock, properties(), fulfillment,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        when(events.findByEventKey(any())).thenReturn(Optional.empty());
        when(events.save(any(PaymentEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(providerPayments.findByProviderPaymentIdForUpdate(any())).thenAnswer(invocation ->
                Optional.ofNullable(records.get(invocation.getArgument(0))));
        when(providerPayments.save(any(ProviderPaymentRecord.class))).thenAnswer(invocation -> {
            ProviderPaymentRecord record = invocation.getArgument(0);
            records.put(record.getProviderPaymentId(), record);
            return record;
        });
        when(providerPayments.existsByAttemptOrderIdAndFundsOrderTrue(any())).thenAnswer(invocation ->
                records.values().stream().anyMatch(ProviderPaymentRecord::isFundsOrder));
    }

    @Test
    void twoPaymentIdsOnOnePreferencePersistAndSecondIsIndividuallyRefunded() {
        CustomerOrder order = order(NOW.plusSeconds(300));
        PaymentAttempt attempt = readyAttempt(order, "pref-1");
        lock(attempt, order);

        assertTrue(service.processWebhook(
                payment(attempt, "pref-1", "123", "approved", NOW.minusSeconds(1), false),
                "123", "request-1", "{}").isEmpty());
        Optional<RefundInstruction> duplicate = service.processWebhook(
                payment(attempt, "pref-1", "124", "approved", NOW, false),
                "124", "request-2", "{}");

        assertEquals(2, records.size());
        assertTrue(records.get("123").isFundsOrder());
        assertFalse(records.get("124").isFundsOrder());
        assertTrue(duplicate.isPresent());
        assertEquals(PaymentStatus.APPROVED, order.getPaymentStatus());

        service.applyRefundResult(duplicate.get(),
                new RefundResult("refund-124", "approved", new BigDecimal("100.00")));

        assertEquals(PaymentStatus.APPROVED, order.getPaymentStatus());
        assertEquals("APPROVED", records.get("124").getRefundStatus());
    }

    @Test
    void approvalFromAnotherPreferenceForPaidOrderIsAlsoRefunded() {
        CustomerOrder order = order(NOW.plusSeconds(300));
        PaymentAttempt first = readyAttempt(order, "pref-1");
        PaymentAttempt second = readyAttempt(order, "pref-2");
        lock(first, order);
        service.processWebhook(payment(first, "pref-1", "123", "approved", NOW, false),
                "123", "request-1", "{}");
        lock(second, order);

        Optional<RefundInstruction> refund = service.processWebhook(
                payment(second, "pref-2", "124", "approved", NOW, false),
                "124", "request-2", "{}");

        assertTrue(refund.isPresent());
        assertEquals(OrderStatus.PAID, order.getStatus());
        assertEquals(PaymentStatus.APPROVED, order.getPaymentStatus());
    }

    @Test
    void latePaymentKeepsOrderPendingUntilRefundIsApprovedForExpectedAmount() {
        CustomerOrder order = order(NOW.minusSeconds(60));
        PaymentAttempt attempt = readyAttempt(order, "pref-1");
        lock(attempt, order);

        RefundInstruction refund = service.processWebhook(
                payment(attempt, "pref-1", "123", "approved", NOW, false),
                "123", "request-1", "{}").orElseThrow();

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals(PaymentStatus.REFUND_PENDING, order.getPaymentStatus());
        verify(stock).release(order);

        service.applyRefundResult(refund, new RefundResult("refund-1", "pending", new BigDecimal("100.00")));
        assertEquals(PaymentStatus.REFUND_PENDING, order.getPaymentStatus());
        service.applyRefundResult(refund, new RefundResult("refund-1", "in_process", new BigDecimal("100.00")));
        assertEquals("PENDING", records.get("123").getRefundStatus());
        service.applyRefundResult(refund, new RefundResult("refund-1", "rejected", new BigDecimal("100.00")));
        assertEquals(PaymentStatus.REFUND_PENDING, order.getPaymentStatus());
        service.applyRefundResult(refund, new RefundResult("refund-1", "approved", new BigDecimal("90.00")));
        assertEquals(PaymentStatus.REFUND_PENDING, order.getPaymentStatus());
        assertEquals("PENDING", records.get("123").getRefundStatus());
        service.applyRefundResult(refund, new RefundResult("refund-1", "approved", new BigDecimal("100.00")));
        assertEquals(PaymentStatus.REFUNDED, order.getPaymentStatus());
    }

    @Test
    void activePreferenceIsReusedRegardlessOfIdempotencyKey() {
        CustomerOrder order = order(NOW.plusSeconds(300));
        PaymentAttempt attempt = readyAttempt(order, "pref-1");
        when(orders.findByIdAndUserIdForUpdate(42L, 7L)).thenReturn(Optional.of(order));
        when(attempts.findActiveByOrderId(any(), any())).thenReturn(List.of(attempt));

        PaymentPreparation result = service.prepare(42L, 7L, "different-key");

        assertFalse(result.created());
        assertEquals(attempt.getPublicId(), result.response().attemptId());
        verify(attempts, never()).save(any());
    }

    @Test
    void activePreferenceIsNotReusedAfterOrderLeavesPendingPayment() {
        CustomerOrder order = order(NOW.plusSeconds(300));
        PaymentAttempt attempt = readyAttempt(order, "pref-1");
        order.approveMercadoPagoPayment();
        when(orders.findByIdAndUserIdForUpdate(42L, 7L)).thenReturn(Optional.of(order));
        when(attempts.findActiveByOrderId(any(), any())).thenReturn(List.of(attempt));

        assertThrows(InvalidRequestException.class,
                () -> service.prepare(42L, 7L, "different-key"));

        verify(attempts, never()).findActiveByOrderId(any(), any());
    }

    @Test
    void requiresEmailToRemainVerifiedBeforeReusingOrCreatingAPreference() {
        CustomerOrder order = order(NOW.plusSeconds(300), false, true);
        when(orders.findByIdAndUserIdForUpdate(42L, 7L)).thenReturn(Optional.of(order));

        assertThrows(EmailVerificationRequiredException.class,
                () -> service.prepare(42L, 7L, "payment-key"));

        verify(attempts, never()).findActiveByOrderId(any(), any());
        verify(attempts, never()).save(any());
    }

    @Test
    void rejectsLegacyOrdersWithoutFulfillmentSnapshot() {
        CustomerOrder order = order(NOW.plusSeconds(300), true, false);
        when(orders.findByIdAndUserIdForUpdate(42L, 7L)).thenReturn(Optional.of(order));

        assertThrows(BusinessRuleException.class,
                () -> service.prepare(42L, 7L, "payment-key"));

        verify(attempts, never()).findActiveByOrderId(any(), any());
        verify(attempts, never()).save(any());
    }

    @Test
    void rejectsAnOrderWhosePickupCodeIsNoLongerActiveBeforeRetryingAPreference() {
        CustomerOrder order = order(NOW.plusSeconds(300), true, true, "CORDOBA-NORTE");
        when(orders.findByIdAndUserIdForUpdate(42L, 7L)).thenReturn(Optional.of(order));

        assertThrows(BusinessRuleException.class,
                () -> service.prepare(42L, 7L, "payment-key"));

        verify(attempts, never()).findActiveByOrderId(any(), any());
        verify(attempts, never()).save(any());
    }

    @Test
    void rejectsPickupWhosePublicSnapshotHasChangedBeforePayment() {
        CustomerOrder order = orderWithChangedPickupSnapshot(NOW.plusSeconds(300));
        when(orders.findByIdAndUserIdForUpdate(42L, 7L)).thenReturn(Optional.of(order));

        assertThrows(BusinessRuleException.class,
                () -> service.prepare(42L, 7L, "payment-key"));

        verify(attempts, never()).save(any(PaymentAttempt.class));
    }

    @Test
    void approvedWebhookForChangedPickupIsCancelledAndQueuedForRefund() {
        CustomerOrder order = orderWithChangedPickupSnapshot(NOW.plusSeconds(300));
        PaymentAttempt attempt = readyAttempt(order, "pref-1");
        lock(attempt, order);

        Optional<RefundInstruction> refund = service.processWebhook(
                payment(attempt, "pref-1", "123", "approved", NOW, false),
                "123", "request-1", "{}");

        assertTrue(refund.isPresent());
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals(PaymentStatus.REFUND_PENDING, order.getPaymentStatus());
        assertFalse(records.get("123").isFundsOrder());
        verify(stock).release(order);
    }

    @Test
    void staleProviderSnapshotDoesNotOverwriteNewerPaymentState() {
        CustomerOrder order = order(NOW.plusSeconds(300));
        PaymentAttempt attempt = readyAttempt(order, "pref-1");
        lock(attempt, order);
        service.processWebhook(payment(attempt, "pref-1", "123", "approved", NOW, false),
                "123", "request-1", "{}");
        ProviderPayment stale = new ProviderPayment(
                "123", attempt.getPublicId().toString(), "pref-1", "99", new BigDecimal("100.00"), "ARS",
                "charged_back", "charged_back-detail", null, NOW, false,
                "regular_payment", BigDecimal.ZERO, "stale-payload");

        service.processWebhook(stale, "123", "request-2", "{}");

        assertEquals(PaymentStatus.APPROVED, order.getPaymentStatus());
        assertEquals("approved", records.get("123").getProviderStatus());
    }

    @Test
    void reconciliationClaimsOnlyConfiguredLookbackWindow() {
        Instant cutoff = NOW.minus(Duration.ofDays(30));
        when(attempts.lockReconciliationsDue(NOW, cutoff)).thenReturn(List.of());

        assertTrue(service.claimReconciliations().isEmpty());

        verify(attempts).lockReconciliationsDue(NOW, cutoff);
    }

    @Test
    void rejectsSandboxPaymentMarkedAsLive() {
        CustomerOrder order = order(NOW.plusSeconds(300));
        PaymentAttempt attempt = readyAttempt(order, "pref-1");
        lock(attempt, order);

        assertThrows(InvalidRequestException.class, () -> service.processWebhook(
                payment(attempt, "pref-1", "123", "approved", NOW, true),
                "123", "request-1", "{}"));
        assertTrue(records.isEmpty());
    }

    @Test
    void mediationBlocksFulfillmentWithoutBeingReportedAsRefund() {
        CustomerOrder order = order(NOW.plusSeconds(300));
        PaymentAttempt attempt = readyAttempt(order, "pref-1");
        lock(attempt, order);
        service.processWebhook(payment(attempt, "pref-1", "123", "approved", NOW, false),
                "123", "request-1", "{}");
        service.processWebhook(payment(attempt, "pref-1", "123", "in_mediation", null, false),
                "123", "request-2", "{}");

        assertEquals(PaymentStatus.IN_MEDIATION, order.getPaymentStatus());
        assertThrows(RuntimeException.class, () -> order.transitionTo(OrderStatus.PREPARING));
    }

    private void lock(PaymentAttempt attempt, CustomerOrder order) {
        when(attempts.findByPublicIdForUpdate(attempt.getPublicId())).thenReturn(Optional.of(attempt));
        when(orders.findByIdForUpdate(any())).thenReturn(Optional.of(order));
    }

    private PaymentAttempt readyAttempt(CustomerOrder order, String preferenceId) {
        PaymentAttempt attempt = new PaymentAttempt(order, "payment-key-" + preferenceId);
        attempt.preferenceCreated(preferenceId, "https://sandbox");
        return attempt;
    }

    private ProviderPayment payment(
            PaymentAttempt attempt, String preferenceId, String id, String status, Instant approvedAt, boolean live) {
        return new ProviderPayment(
                id, attempt.getPublicId().toString(), preferenceId, "99", new BigDecimal("100.00"), "ARS",
                status, status + "-detail", approvedAt, NOW.plusSeconds(Long.parseLong(id)), live,
                "regular_payment", "refunded".equals(status) ? new BigDecimal("100.00") : BigDecimal.ZERO,
                "payload-" + id + status);
    }

    private CustomerOrder order(Instant expiry) {
        return order(expiry, true, true);
    }

    private CustomerOrder order(Instant expiry, boolean emailVerified, boolean withFulfillment) {
        return order(expiry, emailVerified, withFulfillment, "CORDOBA-CENTRO");
    }

    private CustomerOrder orderWithChangedPickupSnapshot(Instant expiry) {
        return order(expiry, true, true, "CORDOBA-CENTRO", "Previous pickup name");
    }

    private CustomerOrder order(
            Instant expiry, boolean emailVerified, boolean withFulfillment, String pickupCode) {
        return order(expiry, emailVerified, withFulfillment, pickupCode, "Current pickup name");
    }

    private CustomerOrder order(
            Instant expiry, boolean emailVerified, boolean withFulfillment, String pickupCode, String pickupName) {
        Product product = Mockito.mock(Product.class);
        when(product.getName()).thenReturn("Keyboard");
        when(product.getPrice()).thenReturn(new BigDecimal("100.00"));
        ProductVariant variant = Mockito.mock(ProductVariant.class);
        when(variant.getId()).thenReturn(1L);
        when(variant.getProduct()).thenReturn(product);
        when(variant.getColorName()).thenReturn("Black");
        UserAccount user = Mockito.mock(UserAccount.class);
        when(user.isEmailVerified()).thenReturn(emailVerified);
        PickupLocationSnapshot pickup = withFulfillment
                ? new PickupLocationSnapshot(
                        pickupCode, pickupName, List.of("Current address", "Local 4"),
                        "Cordoba", "X", "5000", "Current instructions", "Current hours")
                : null;
        return new CustomerOrder(
                user, List.of(new OrderItem(variant, 1)), new BigDecimal("100.00"), expiry, null, null,
                withFulfillment ? FulfillmentMethod.PICKUP : null, pickup);
    }

    private static FulfillmentProperties fulfillmentProperties() {
        return new FulfillmentProperties(new FulfillmentProperties.Pickup(
                true, "CORDOBA-CENTRO", "Current pickup name", List.of("Current address", "Local 4"),
                "Cordoba", "X", "5000", "Current instructions", "Current hours"));
    }

    private MercadoPagoProperties properties() {
        return new MercadoPagoProperties(
                true, MercadoPagoEnvironment.SANDBOX, "TEST-access-token", "webhook-secret", "99",
                URI.create("https://store.example"), URI.create("https://api.example"),
                Duration.ofSeconds(1), Duration.ofSeconds(2),
                false, Duration.ofMinutes(5), Duration.ofDays(30));
    }
}
