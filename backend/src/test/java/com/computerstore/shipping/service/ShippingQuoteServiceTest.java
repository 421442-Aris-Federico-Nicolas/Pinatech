package com.computerstore.shipping.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import com.computerstore.catalog.domain.*;
import com.computerstore.catalog.repository.ProductVariantRepository;
import com.computerstore.common.exception.*;
import com.computerstore.shipping.config.ZipnovaProperties;
import com.computerstore.shipping.domain.ShippingQuote;
import com.computerstore.shipping.dto.ShippingQuoteRequest;
import com.computerstore.shipping.gateway.ZipnovaGateway;
import com.computerstore.shipping.repository.ShippingQuoteRepository;
import com.computerstore.user.domain.*;
import com.computerstore.user.repository.*;
import com.computerstore.guest.domain.GuestCheckoutSession;
import com.computerstore.guest.dto.*;
import com.computerstore.guest.service.GuestCheckoutEligibilityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ShippingQuoteServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-04T12:00:00Z");

    @Test
    void loadsVariantsWithoutPessimisticLockWhenQuoting() {
        Fixtures f = fixtures(1L);
        when(f.users.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(f.owner));
        when(f.variantsRepository.findActiveForShippingQuote(10L)).thenReturn(Optional.of(f.variants.get(0)));
        when(f.gateway.quote(any())).thenReturn(List.of());
        when(f.quotes.saveAll(any())).thenReturn(List.of());

        var request = new ShippingQuoteRequest(List.of(new ShippingQuoteRequest.Item(10L, 1)));
        assertDoesNotThrow(() -> f.service.quote(1L, request));

        verify(f.variantsRepository).findActiveForShippingQuote(10L);
        verify(f.variantsRepository, never()).findByIdAndActiveTrueAndProduct_ActiveTrue(anyLong());
    }

    @Test
    void rejectsExpiredQuote() {
        Fixtures f = fixtures(1L);
        ShippingQuote quote = quote(f.owner, f.inputs, f.variants, f.address, NOW.minusSeconds(1200), NOW.minusSeconds(1));
        when(f.quotes.findByIdForUpdate(quote.getId())).thenReturn(Optional.of(quote));
        assertThrows(InvalidRequestException.class, () -> f.service.validateForOrder(quote.getId(), f.owner, f.inputs, f.variants));
    }

    @Test
    void rejectsCartTamperingAfterQuote() {
        Fixtures f = fixtures(1L);
        ShippingQuote quote = quote(f.owner, f.inputs, f.variants, f.address, NOW, NOW.plusSeconds(900));
        when(f.quotes.findByIdForUpdate(quote.getId())).thenReturn(Optional.of(quote));
        var changed = List.of(new ShippingHashes.ItemQuantity(10L, 2));
        assertThrows(InvalidRequestException.class, () -> f.service.validateForOrder(quote.getId(), f.owner, changed, f.variants));
    }

    @Test
    void hidesQuoteOwnedByAnotherCustomer() {
        Fixtures f = fixtures(2L);
        UserAccount other = user(1L); UserAddress otherAddress = address();
        ShippingQuote quote = quote(other, f.inputs, f.variants, otherAddress, NOW, NOW.plusSeconds(900));
        when(f.quotes.findByIdForUpdate(quote.getId())).thenReturn(Optional.of(quote));
        assertThrows(ResourceNotFoundException.class, () -> f.service.validateForOrder(quote.getId(), f.owner, f.inputs, f.variants));
    }

    @Test
    void rejectsRegisteredGuestEmailBeforeLoadingProductsOrCallingTheProvider() {
        Fixtures f = fixtures(1L);
        doThrow(new GuestCheckoutAccountRequiredException()).when(f.guestEligibility)
                .requireUnregistered("ada@example.com");
        var request = new GuestShippingQuoteRequest(
                List.of(new ShippingQuoteRequest.Item(10L, 1)),
                new GuestCustomerRequest("Ada", "Lovelace", " ADA@EXAMPLE.COM ", "3515550000", "12345678"),
                new GuestDeliveryAddressRequest("San Martin", "10", null, "Cordoba", "X", "5000", null, "AR"));
        var session = new GuestCheckoutSession("a".repeat(64), "b".repeat(64), NOW, NOW.plusSeconds(3600));

        assertThrows(GuestCheckoutAccountRequiredException.class, () -> f.service.quoteGuest(session, request));

        verify(f.variantsRepository, never()).findActiveForShippingQuote(anyLong());
        verify(f.gateway, never()).quote(any());
        verify(f.quotes, never()).saveAll(any());
    }

    private Fixtures fixtures(long ownerId) {
        UserAccount owner = user(ownerId); UserAddress address = address();
        UserAddressRepository addresses = mock(UserAddressRepository.class); when(addresses.findById(ownerId)).thenReturn(Optional.of(address));
        Product product = mock(Product.class); when(product.getPrice()).thenReturn(new BigDecimal("100.00"));
        when(product.getShippingWeightGrams()).thenReturn(500); when(product.getShippingHeightCm()).thenReturn(10);
        when(product.getShippingWidthCm()).thenReturn(20); when(product.getShippingLengthCm()).thenReturn(30);
        when(product.getShippingClassificationId()).thenReturn(1); when(product.hasCompleteShippingData()).thenReturn(true);
        ProductVariant variant = mock(ProductVariant.class); when(variant.getId()).thenReturn(10L); when(variant.getProduct()).thenReturn(product);
        List<ProductVariant> variants = List.of(variant); List<ShippingHashes.ItemQuantity> inputs = List.of(new ShippingHashes.ItemQuantity(10L, 1));
        ShippingQuoteRepository quotes = mock(ShippingQuoteRepository.class);
        ZipnovaGateway gateway = mock(ZipnovaGateway.class); UserAccountRepository users = mock(UserAccountRepository.class);
        ProductVariantRepository variantsRepository = mock(ProductVariantRepository.class);
        GuestCheckoutEligibilityService guestEligibility = mock(GuestCheckoutEligibilityService.class);
        var service = new ShippingQuoteService(properties(), gateway, users, addresses, variantsRepository, quotes,
                new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC), guestEligibility);
        return new Fixtures(owner, address, variants, inputs, quotes, gateway, users, variantsRepository,
                guestEligibility, service);
    }
    private ShippingQuote quote(UserAccount user, List<ShippingHashes.ItemQuantity> inputs, List<ProductVariant> variants,
                                UserAddress address, Instant created, Instant expires) {
        var option = new ZipnovaGateway.QuoteOption(3, "Andreani", "standard", "Estandar", "carrier_pickup",
                new BigDecimal("121.00"), null, List.of());
        return new ShippingQuote(user, ShippingHashes.cart(inputs, variants),
                ShippingHashes.profile(user, address, "Córdoba"), option, "[]", created, expires);
    }
    private UserAccount user(long id) { UserAccount user = mock(UserAccount.class); when(user.getId()).thenReturn(id);
        when(user.isEmailVerified()).thenReturn(true);
        when(user.getFirstName()).thenReturn("Ada"); when(user.getLastName()).thenReturn("Lovelace");
        when(user.getEmail()).thenReturn("ada@example.com"); when(user.getPhone()).thenReturn("3515550000");
        when(user.getDocumentNumber()).thenReturn("12345678"); return user; }
    private UserAddress address() { UserAddress a = mock(UserAddress.class); when(a.getCountryCode()).thenReturn("AR");
        when(a.getProvinceCode()).thenReturn("X"); when(a.getStreet()).thenReturn("San Martin");
        when(a.getStreetNumber()).thenReturn("10"); when(a.getLocality()).thenReturn("Cordoba");
        when(a.getPostalCode()).thenReturn("5000"); return a; }
    private ZipnovaProperties properties() { return new ZipnovaProperties(true, true, "token", "secret", 7L, 12L,
            "pinatech", "dynamic", Duration.ofMinutes(15), Duration.ofSeconds(1), Duration.ofSeconds(2),
            "012345678901234567890123", Duration.ofMinutes(10)); }
    private record Fixtures(UserAccount owner, UserAddress address, List<ProductVariant> variants,
            List<ShippingHashes.ItemQuantity> inputs, ShippingQuoteRepository quotes, ZipnovaGateway gateway,
            UserAccountRepository users, ProductVariantRepository variantsRepository,
            GuestCheckoutEligibilityService guestEligibility, ShippingQuoteService service) {}
}
