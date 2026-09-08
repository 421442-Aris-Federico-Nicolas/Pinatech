package com.computerstore.guest.service;

import com.computerstore.catalog.domain.ProductVariant;
import com.computerstore.catalog.repository.ProductVariantRepository;
import com.computerstore.common.exception.*;
import com.computerstore.guest.domain.GuestCheckoutSession;
import com.computerstore.guest.dto.*;
import com.computerstore.guest.repository.GuestCheckoutSessionRepository;
import com.computerstore.email.OrderEmailOutboxService;
import com.computerstore.email.TransactionalEmailService;
import com.computerstore.order.config.OrderProperties;
import com.computerstore.order.domain.*;
import com.computerstore.order.dto.OrderResponseMapper;
import com.computerstore.order.dto.CreateOrderRequest;
import com.computerstore.order.repository.CustomerOrderRepository;
import com.computerstore.order.service.FulfillmentPolicy;
import com.computerstore.order.service.OrderStockService;
import com.computerstore.payment.config.BankTransferProperties;
import com.computerstore.payment.config.MercadoPagoProperties;
import com.computerstore.shipping.service.ShippingHashes;
import com.computerstore.shipping.service.ShippingQuoteService;
import com.computerstore.user.domain.UserAccount;
import com.computerstore.user.repository.UserAccountRepository;
import com.computerstore.user.service.AccountEmailLockService;
import jakarta.servlet.http.HttpServletRequest;
import java.math.*;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GuestOrderService {
    private static final BigDecimal TRANSFER_DISCOUNT = new BigDecimal("0.10");
    private final CustomerOrderRepository orders;
    private final ProductVariantRepository variants;
    private final OrderStockService stock;
    private final OrderProperties orderProperties;
    private final FulfillmentPolicy fulfillment;
    private final BankTransferProperties bankTransfer;
    private final ShippingQuoteService shippingQuotes;
    private final GuestRequestSecurity security;
    private final GuestOrderAccessService access;
    private final UserAccountRepository users;
    private final GuestCheckoutSessionRepository sessions;
    private final MercadoPagoProperties mercadoPago;
    private final TransactionalEmailService email;
    private final OrderEmailOutboxService outbox;
    private final GuestCheckoutEligibilityService eligibility;
    private final AccountEmailLockService emailLock;
    private final SecureRandom random = new SecureRandom();

    public GuestOrderService(CustomerOrderRepository orders, ProductVariantRepository variants,
            OrderStockService stock, OrderProperties orderProperties, FulfillmentPolicy fulfillment,
            BankTransferProperties bankTransfer, ShippingQuoteService shippingQuotes,
            GuestRequestSecurity security, GuestOrderAccessService access, UserAccountRepository users,
            GuestCheckoutSessionRepository sessions, MercadoPagoProperties mercadoPago,
            TransactionalEmailService email, OrderEmailOutboxService outbox,
            GuestCheckoutEligibilityService eligibility, AccountEmailLockService emailLock) {
        this.orders = orders; this.variants = variants; this.stock = stock; this.orderProperties = orderProperties;
        this.fulfillment = fulfillment; this.bankTransfer = bankTransfer; this.shippingQuotes = shippingQuotes;
        this.security = security; this.access = access; this.users = users; this.sessions = sessions;
        this.mercadoPago = mercadoPago; this.email = email; this.outbox = outbox;
        this.eligibility = eligibility; this.emailLock = emailLock;
    }

    @Transactional
    public Created create(GuestCreateOrderRequest request, String suppliedKey, HttpServletRequest servletRequest) {
        security.validateOrigin(servletRequest);
        GuestCheckoutSession authenticatedSession = security.session(servletRequest, true);
        GuestCheckoutSession session = sessions.findByIdForUpdate(authenticatedSession.getId())
                .orElseThrow(this::notFound);
        String key = idempotencyKey(suppliedKey);
        var customer = GuestCheckoutNormalizer.customer(request.customer());
        var address = request.fulfillmentMethod() == FulfillmentMethod.DELIVERY
                ? GuestCheckoutNormalizer.address(request.deliveryAddress(), customer) : null;
        String requestHash = requestHash(request, customer, address);
        var existing = orders.findByGuestSessionIdAndIdempotencyKey(session.getId(), key);
        if (existing.isPresent()) {
            if (!requestHash.equals(existing.get().getRequestHash())) {
                throw new DuplicateResourceException("Idempotency key was already used for a different order.");
            }
            return new Created(false, response(existing.get(), null));
        }
        eligibility.rateLimitOrderCheck(servletRequest, session, customer.email());
        emailLock.lock(customer.email());
        eligibility.requireUnregistered(customer.email());
        if (orders.existsByGuestSessionIdAndStatus(session.getId(), OrderStatus.PENDING_PAYMENT)) {
            throw new DuplicateResourceException("This guest session already has a pending payment order.");
        }
        if (request.paymentMethod() == PaymentMethod.MERCADO_PAGO) mercadoPago.requireEnabled();
        if (request.paymentMethod() == PaymentMethod.BANK_TRANSFER) {
            if (!bankTransfer.available()) throw new InvalidRequestException("Bank transfer payment is not available.");
            if (!session.isEmailVerified(customer.email())) throw new EmailVerificationRequiredException();
        }

        List<CreateOrderRequest.Item> inputs = request.items().stream()
                .sorted(Comparator.comparing(CreateOrderRequest.Item::variantId)).toList();
        Set<Long> ids = new HashSet<>();
        List<OrderItem> items = new ArrayList<>();
        List<ProductVariant> selected = new ArrayList<>();
        for (var input : inputs) {
            if (!ids.add(input.variantId())) throw new InvalidRequestException(
                    "An order cannot contain the same product variant more than once.");
            ProductVariant variant = variants.findByIdAndActiveTrueAndProduct_ActiveTrue(input.variantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product variant not found."));
            items.add(new OrderItem(variant, input.quantity()));
            selected.add(variant);
        }
        var pickup = request.fulfillmentMethod() == FulfillmentMethod.PICKUP
                ? fulfillment.select(request.fulfillmentMethod(), request.pickupLocationCode(), request.pickupLocationVersion())
                : null;
        ShippingQuoteService.ValidatedQuote delivery = request.fulfillmentMethod() == FulfillmentMethod.DELIVERY
                ? shippingQuotes.validateForGuestOrder(request.shippingQuoteId(), session, customer, address,
                    request.items().stream().map(ShippingHashes::item).toList(), selected)
                : null;
        BigDecimal subtotal = items.stream().map(OrderItem::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal discount = request.paymentMethod() == PaymentMethod.BANK_TRANSFER
                ? subtotal.multiply(TRANSFER_DISCOUNT).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
        BigDecimal shipping = delivery == null ? BigDecimal.ZERO.setScale(2) : delivery.quote().getAmount();
        Instant now = Instant.now();
        boolean transfer = request.paymentMethod() == PaymentMethod.BANK_TRANSFER;
        String rawAccessToken = randomToken();
        CustomerOrder order = orders.save(new CustomerOrder(session, customer.snapshot(),
                GuestRequestSecurity.sha256(rawAccessToken), items, subtotal, BigDecimal.ZERO.setScale(2), discount,
                shipping, request.paymentMethod(), now.plus(transfer ? bankTransfer.proofTtl()
                        : orderProperties.reservationTtl()), transfer ? now.plus(bankTransfer.proofTtl()) : null,
                transfer ? bankTransfer.snapshot() : null, key, requestHash, request.fulfillmentMethod(), pickup,
                delivery == null ? null : delivery.quote(), delivery == null ? null : delivery.address()));
        if (delivery != null) delivery.quote().consume(order);
        stock.reserve(order);
        email.sendGuestOrderCreated(customer.email(), customer.firstName(), order.getPublicId(), rawAccessToken);
        outbox.enqueueSellerOrderCreated(order);
        return new Created(true, response(order, rawAccessToken));
    }

    @Transactional(readOnly = true)
    public GuestOrderResponse get(UUID publicId, HttpServletRequest request) {
        return response(access.require(publicId, request, false), null);
    }

    @Transactional
    public GuestOrderResponse claim(UUID publicId, Long userId, HttpServletRequest request) {
        security.validateOrigin(request);
        CustomerOrder current = orders.findByPublicId(publicId).orElseThrow(this::notFound);
        access.authorize(current, request, true);
        CustomerOrder order = orders.findByPublicIdForUpdate(publicId).orElseThrow(this::notFound);
        UserAccount user = users.findByIdForUpdate(userId).filter(UserAccount::isActive).filter(UserAccount::isEmailVerified)
                .filter(candidate -> candidate.getEmail().equalsIgnoreCase(order.getBuyer().getEmail()))
                .orElseThrow(this::notFound);
        try {
            order.claim(user);
        } catch (IllegalArgumentException exception) {
            throw notFound();
        }
        return response(order, null);
    }

    private GuestOrderResponse response(CustomerOrder order, String token) {
        return new GuestOrderResponse(order.getPublicId(), OrderResponseMapper.toResponse(order), token);
    }

    private String requestHash(GuestCreateOrderRequest request, GuestCheckoutNormalizer.Customer customer,
                               GuestCheckoutNormalizer.Address address) {
        String items = request.items().stream().sorted(Comparator.comparing(CreateOrderRequest.Item::variantId)
                .thenComparing(CreateOrderRequest.Item::quantity)).map(item -> item.variantId() + ":" + item.quantity())
                .reduce((left, right) -> left + "," + right).orElse("");
        String canonical = canonical(request.paymentMethod().name(), request.fulfillmentMethod().name(),
                safe(request.pickupLocationCode()), safe(request.pickupLocationVersion()),
                request.shippingQuoteId() == null ? "" : request.shippingQuoteId(), items,
                customer.firstName(), customer.lastName(), customer.email(), customer.phone(), customer.documentNumber(),
                address == null ? "" : canonical(address.street(), address.streetNumber(),
                        safe(address.floorApartment()), address.locality(), address.provinceCode(), address.postalCode(),
                        safe(address.reference()), address.countryCode()));
        return GuestRequestSecurity.sha256(canonical);
    }

    private String idempotencyKey(String supplied) {
        String key = supplied == null ? "" : supplied.trim();
        if (key.isEmpty() || key.length() > 100) throw new InvalidRequestException(
                "Idempotency-Key must contain between 1 and 100 characters.");
        return key;
    }

    private String randomToken() {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private String safe(String value) { return value == null ? "" : value.trim(); }
    private String canonical(Object... values) {
        StringBuilder result = new StringBuilder();
        for (Object value : values) {
            String text = value == null ? "" : value.toString();
            result.append(text.length()).append(':').append(text);
        }
        return result.toString();
    }
    private ResourceNotFoundException notFound() { return new ResourceNotFoundException("Guest order not found."); }
    public record Created(boolean created, GuestOrderResponse response) {}
}
