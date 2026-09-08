package com.computerstore.guest.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.computerstore.catalog.domain.*;
import com.computerstore.catalog.repository.ProductVariantRepository;
import com.computerstore.common.exception.EmailVerificationRequiredException;
import com.computerstore.guest.domain.GuestCheckoutSession;
import com.computerstore.guest.dto.*;
import com.computerstore.order.config.*;
import com.computerstore.order.domain.*;
import com.computerstore.order.dto.CreateOrderRequest;
import com.computerstore.order.repository.CustomerOrderRepository;
import com.computerstore.order.service.*;
import com.computerstore.payment.config.BankTransferProperties;
import com.computerstore.shipping.domain.ShippingQuote;
import com.computerstore.shipping.service.ShippingQuoteService;
import com.computerstore.user.repository.UserAccountRepository;
import com.computerstore.user.domain.UserAccount;
import com.computerstore.user.service.AccountEmailLockService;
import com.computerstore.guest.repository.GuestCheckoutSessionRepository;
import com.computerstore.payment.config.MercadoPagoProperties;
import com.computerstore.email.TransactionalEmailService;
import com.computerstore.email.OrderEmailOutboxService;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GuestOrderServiceTest {
    private final CustomerOrderRepository orders = mock(CustomerOrderRepository.class);
    private final ProductVariantRepository variants = mock(ProductVariantRepository.class);
    private final OrderStockService stock = mock(OrderStockService.class);
    private final ShippingQuoteService quotes = mock(ShippingQuoteService.class);
    private final GuestRequestSecurity security = mock(GuestRequestSecurity.class);
    private final GuestOrderAccessService access = mock(GuestOrderAccessService.class);
    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final GuestCheckoutSessionRepository sessions = mock(GuestCheckoutSessionRepository.class);
    private final MercadoPagoProperties mercadoPago = mock(MercadoPagoProperties.class);
    private final TransactionalEmailService email = mock(TransactionalEmailService.class);
    private final OrderEmailOutboxService outbox = mock(OrderEmailOutboxService.class);
    private final GuestCheckoutEligibilityService eligibility = mock(GuestCheckoutEligibilityService.class);
    private final AccountEmailLockService emailLock = mock(AccountEmailLockService.class);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final GuestCheckoutSession session = new GuestCheckoutSession("a".repeat(64), "b".repeat(64),
            Instant.now(), Instant.now().plus(Duration.ofHours(1)));
    private GuestOrderService service;

    @BeforeEach
    void setUp() {
        var pickup = new FulfillmentProperties.Pickup(true, "CENTRO", "Pinatech Centro", List.of("Calle 1"),
                "Cordoba", "X", "5000", "Traer DNI", "Lunes a viernes");
        service = new GuestOrderService(orders, variants, stock, new OrderProperties(Duration.ofMinutes(15), 100),
                new FulfillmentPolicy(new FulfillmentProperties(pickup)), new BankTransferProperties(true,
                "Pinatech", "30-12345678-9", "Banco", "pinatech", "1234567890123456789012", "ARS",
                Duration.ofHours(24)), quotes, security, access, users, sessions, mercadoPago, email, outbox,
                eligibility, emailLock);
        when(security.session(request, true)).thenReturn(session);
        when(sessions.findByIdForUpdate(session.getId())).thenReturn(Optional.of(session));
        when(orders.save(any(CustomerOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ProductVariant availableVariant = variant();
        when(variants.findByIdAndActiveTrueAndProduct_ActiveTrue(7L)).thenReturn(Optional.of(availableVariant));
    }

    @Test
    void createsPickupWithImmutableBuyerSnapshotAndAccessToken() {
        var result = service.create(orderRequest(PaymentMethod.MERCADO_PAGO, FulfillmentMethod.PICKUP),
                "guest-order-1", request);

        assertTrue(result.created());
        assertNotNull(result.response().publicId());
        assertNotNull(result.response().accessToken());
        var saved = org.mockito.ArgumentCaptor.forClass(CustomerOrder.class);
        verify(orders).save(saved.capture());
        assertTrue(saved.getValue().isGuest());
        assertEquals("ada@example.com", saved.getValue().getBuyer().getEmail());
        assertEquals("12345678", saved.getValue().getBuyer().getDocumentNumber());
        assertEquals(FulfillmentMethod.PICKUP, saved.getValue().getFulfillmentMethod());
        verify(stock).reserve(saved.getValue());
        verify(email).sendGuestOrderCreated(eq("ada@example.com"), eq("Ada"), eq(saved.getValue().getPublicId()),
                eq(result.response().accessToken()));
        verify(outbox).enqueueSellerOrderCreated(saved.getValue());
    }

    @Test
    void createsDeliveryOnlyAfterRevalidatingGuestQuoteAndProfile() {
        ShippingQuote quote = mock(ShippingQuote.class);
        when(quote.getAmount()).thenReturn(new BigDecimal("1500.00"));
        when(quote.getCarrierId()).thenReturn(9L);
        when(quote.getCarrierName()).thenReturn("Carrier");
        when(quote.getServiceCode()).thenReturn("home");
        when(quote.getServiceName()).thenReturn("Home");
        when(quote.getLogisticType()).thenReturn("crossdock");
        var snapshot = new DeliveryAddressSnapshot("Ada Lovelace", "12345678", "ada@example.com",
                "+54 351 555 0101", "San Martin", "123", null, "Cordoba", "Cordoba", "X", "5000", "AR", null);
        when(quotes.validateForGuestOrder(any(), eq(session), any(), any(), any(), any()))
                .thenReturn(new ShippingQuoteService.ValidatedQuote(quote, snapshot));

        var result = service.create(orderRequest(PaymentMethod.MERCADO_PAGO, FulfillmentMethod.DELIVERY),
                "guest-order-delivery", request);

        assertEquals(new BigDecimal("1500.00"), result.response().order().shippingCost());
        verify(quotes).validateForGuestOrder(any(), eq(session), any(), any(), any(), any());
        verify(quote).consume(any(CustomerOrder.class));
    }

    @Test
    void rejectsGuestTransferUntilTheSameNormalizedEmailIsVerified() {
        assertThrows(EmailVerificationRequiredException.class, () -> service.create(
                orderRequest(PaymentMethod.BANK_TRANSFER, FulfillmentMethod.PICKUP), "guest-transfer", request));
        verify(variants, never()).findByIdAndActiveTrueAndProduct_ActiveTrue(any());
        verify(stock, never()).reserve(any());
    }

    @Test
    void rejectsARegisteredEmailBeforeCreatingANewGuestOrder() {
        doThrow(new com.computerstore.common.exception.GuestCheckoutAccountRequiredException())
                .when(eligibility).requireUnregistered("ada@example.com");

        assertThrows(com.computerstore.common.exception.GuestCheckoutAccountRequiredException.class,
                () -> service.create(orderRequest(PaymentMethod.MERCADO_PAGO, FulfillmentMethod.PICKUP),
                        "registered-email", request));

        verify(emailLock).lock("ada@example.com");
        verify(eligibility).rateLimitOrderCheck(request, session, "ada@example.com");
        verify(orders, never()).existsByGuestSessionIdAndStatus(any(), any());
        verify(mercadoPago, never()).requireEnabled();
        verify(variants, never()).findByIdAndActiveTrueAndProduct_ActiveTrue(any());
        verify(stock, never()).reserve(any());
        verify(email, never()).sendGuestOrderCreated(any(), any(), any(), any());
    }

    @Test
    void rejectsASecondPendingOrderAfterTheIdempotentReplayCheck() {
        when(orders.existsByGuestSessionIdAndStatus(session.getId(), OrderStatus.PENDING_PAYMENT)).thenReturn(true);

        assertThrows(com.computerstore.common.exception.DuplicateResourceException.class, () -> service.create(
                orderRequest(PaymentMethod.MERCADO_PAGO, FulfillmentMethod.PICKUP), "second-order", request));

        verify(variants, never()).findByIdAndActiveTrueAndProduct_ActiveTrue(any());
        verify(stock, never()).reserve(any());
    }

    @Test
    void checksMercadoPagoAvailabilityBeforeLoadingProductsOrReservingStock() {
        doThrow(new com.computerstore.common.exception.BusinessRuleException("Mercado Pago payments are disabled."))
                .when(mercadoPago).requireEnabled();

        assertThrows(com.computerstore.common.exception.BusinessRuleException.class, () -> service.create(
                orderRequest(PaymentMethod.MERCADO_PAGO, FulfillmentMethod.PICKUP), "mp-disabled", request));

        verify(variants, never()).findByIdAndActiveTrueAndProduct_ActiveTrue(any());
        verify(stock, never()).reserve(any());
    }

    @Test
    void replaysTheSameIdempotentOrderWithoutReservingAgainOrReturningTheTokenTwice() {
        var first = service.create(orderRequest(PaymentMethod.MERCADO_PAGO, FulfillmentMethod.PICKUP),
                "guest-idempotent", request);
        var saved = org.mockito.ArgumentCaptor.forClass(CustomerOrder.class);
        verify(orders).save(saved.capture());
        CustomerOrder persisted = saved.getValue();
        when(orders.findByGuestSessionIdAndIdempotencyKey(session.getId(), "guest-idempotent"))
                .thenReturn(Optional.of(persisted));

        var replay = service.create(orderRequest(PaymentMethod.MERCADO_PAGO, FulfillmentMethod.PICKUP),
                "guest-idempotent", request);

        assertFalse(replay.created());
        assertNull(replay.response().accessToken());
        verify(stock, times(1)).reserve(any());
        verify(eligibility, times(1)).requireUnregistered("ada@example.com");
        verify(eligibility, times(1)).rateLimitOrderCheck(request, session, "ada@example.com");
        verify(emailLock, times(1)).lock("ada@example.com");
    }

    @Test
    void claimsOnlyIntoAnAuthenticatedVerifiedAccountWithTheSnapshotEmail() {
        service.create(orderRequest(PaymentMethod.MERCADO_PAGO, FulfillmentMethod.PICKUP), "guest-claim", request);
        var saved = org.mockito.ArgumentCaptor.forClass(CustomerOrder.class);
        verify(orders).save(saved.capture());
        CustomerOrder order = saved.getValue();
        UserAccount account = mock(UserAccount.class);
        when(account.isActive()).thenReturn(true);
        when(account.isEmailVerified()).thenReturn(true);
        when(account.getEmail()).thenReturn("ada@example.com");
        when(orders.findByPublicId(order.getPublicId())).thenReturn(Optional.of(order));
        when(orders.findByPublicIdForUpdate(order.getPublicId())).thenReturn(Optional.of(order));
        when(users.findByIdForUpdate(99L)).thenReturn(Optional.of(account));

        service.claim(order.getPublicId(), 99L, request);

        assertFalse(order.isGuest());
        assertEquals(account, order.getUser());
        assertNull(order.getGuestAccessTokenHash());
        verify(access).authorize(order, request, true);
    }

    private GuestCreateOrderRequest orderRequest(PaymentMethod payment, FulfillmentMethod fulfillment) {
        var customer = new GuestCustomerRequest(" Ada ", " Lovelace ", "ADA@EXAMPLE.COM",
                "+54 351 555 0101", "12.345.678");
        var address = fulfillment == FulfillmentMethod.DELIVERY ? new GuestDeliveryAddressRequest(
                "San Martin", "123", null, "Cordoba", "X", "5000", null, "AR") : null;
        return new GuestCreateOrderRequest(List.of(new CreateOrderRequest.Item(7L, 2)), payment, fulfillment,
                fulfillment == FulfillmentMethod.PICKUP ? "CENTRO" : null,
                fulfillment == FulfillmentMethod.PICKUP
                        ? new FulfillmentPolicy(new FulfillmentProperties(new FulfillmentProperties.Pickup(true,
                        "CENTRO", "Pinatech Centro", List.of("Calle 1"), "Cordoba", "X", "5000", "Traer DNI",
                        "Lunes a viernes"))).activePickupLocation().orElseThrow().version() : null,
                fulfillment == FulfillmentMethod.DELIVERY ? UUID.randomUUID() : null, customer, address);
    }

    private ProductVariant variant() {
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(3L);
        when(product.getName()).thenReturn("Keyboard");
        when(product.getPrice()).thenReturn(new BigDecimal("100.00"));
        ProductVariant variant = mock(ProductVariant.class);
        when(variant.getId()).thenReturn(7L);
        when(variant.getProduct()).thenReturn(product);
        when(variant.getColorName()).thenReturn("Black");
        when(variant.getColorHex()).thenReturn("#000000");
        return variant;
    }
}
