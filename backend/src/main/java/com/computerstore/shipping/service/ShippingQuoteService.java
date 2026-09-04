package com.computerstore.shipping.service;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

import com.computerstore.catalog.domain.ProductVariant;
import com.computerstore.catalog.repository.ProductVariantRepository;
import com.computerstore.common.exception.*;
import com.computerstore.order.domain.DeliveryAddressSnapshot;
import com.computerstore.shipping.config.ZipnovaProperties;
import com.computerstore.shipping.domain.ShippingQuote;
import com.computerstore.shipping.dto.*;
import com.computerstore.shipping.gateway.ZipnovaGateway;
import com.computerstore.shipping.repository.ShippingQuoteRepository;
import com.computerstore.user.domain.*;
import com.computerstore.user.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

@Service
public class ShippingQuoteService {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern PHONE = Pattern.compile("^[+0-9() .-]{6,50}$");
    private static final Pattern DOCUMENT = Pattern.compile("^[0-9.-]{6,50}$");
    private final ZipnovaProperties properties; private final ZipnovaGateway gateway;
    private final UserAccountRepository users; private final UserAddressRepository addresses;
    private final ProductVariantRepository variants; private final ShippingQuoteRepository quotes;
    private final ObjectMapper json; private final Clock clock;

    public ShippingQuoteService(ZipnovaProperties properties, ZipnovaGateway gateway, UserAccountRepository users,
            UserAddressRepository addresses, ProductVariantRepository variants, ShippingQuoteRepository quotes,
            ObjectMapper json, Clock clock) {
        this.properties = properties; this.gateway = gateway; this.users = users; this.addresses = addresses;
        this.variants = variants; this.quotes = quotes; this.json = json; this.clock = clock;
    }

    public ShippingQuoteResponse quote(Long userId, ShippingQuoteRequest request) {
        properties.requireAvailable();
        UserAccount user = activeVerifiedUser(userId);
        UserAddress address = address(userId);
        Profile profile = profile(user, address);
        List<ShippingHashes.ItemQuantity> inputs = request.items().stream().map(ShippingHashes::item).toList();
        List<ProductVariant> products = loadVariants(inputs);
        List<ZipnovaGateway.Item> expanded = expandedItems(products, inputs);
        String cartHash = ShippingHashes.cart(inputs, products);
        String profileHash = ShippingHashes.profile(user, address, profile.province());
        var options = gateway.quote(new ZipnovaGateway.QuoteCommand(profile.destination(), declaredValue(products, inputs), expanded));
        Instant now = Instant.now(clock); Instant expires = now.plus(properties.quoteTtl());
        List<ShippingQuote> pending = new ArrayList<>();
        for (var option : options) {
            String tags = encode(option.tags());
            pending.add(new ShippingQuote(user, cartHash, profileHash, option, tags, now, expires));
        }
        List<ShippingQuoteResponse.Option> response = new ArrayList<>();
        for (ShippingQuote quote : quotes.saveAll(pending)) {
            response.add(new ShippingQuoteResponse.Option(quote.getId(), quote.getCarrierName(), quote.getServiceCode(),
                    quote.getServiceName(), quote.getLogisticType(), quote.getAmount(), quote.getCurrency(),
                    quote.getEstimatedDeliveryAt(), expires, decodeTags(quote.getTags())));
        }
        return new ShippingQuoteResponse(List.copyOf(response));
    }

    public ValidatedQuote validateForOrder(UUID id, UserAccount user, List<ShippingHashes.ItemQuantity> inputs,
                                           List<ProductVariant> products) {
        properties.requireAvailable();
        if (id == null) throw new InvalidRequestException("A shipping quote is required for delivery.");
        ShippingQuote quote = quotes.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shipping quote not found."));
        UserAddress address = address(user.getId()); Profile profile = profile(user, address);
        if (!quote.getUser().getId().equals(user.getId())) throw new ResourceNotFoundException("Shipping quote not found.");
        if (!quote.getExpiresAt().isAfter(Instant.now(clock))) throw new InvalidRequestException("The shipping quote has expired.");
        if (quote.getConsumedOrder() != null) throw new DuplicateResourceException("The shipping quote was already consumed.");
        if (!quote.getCartHash().equals(ShippingHashes.cart(inputs, products))
                || !quote.getProfileHash().equals(ShippingHashes.profile(user, address, profile.province()))) {
            throw new InvalidRequestException("The cart or delivery profile changed after the shipping quote.");
        }
        return new ValidatedQuote(quote, profile.snapshot());
    }

    private UserAccount activeVerifiedUser(Long id) {
        UserAccount user = users.findByIdAndActiveTrue(id).orElseThrow(() -> new ResourceNotFoundException("User not found."));
        if (!user.isEmailVerified()) throw new EmailVerificationRequiredException();
        return user;
    }
    private UserAddress address(Long id) { return addresses.findById(id)
            .orElseThrow(() -> new InvalidRequestException("A complete delivery address is required.")); }
    private Profile profile(UserAccount user, UserAddress address) {
        if (!"AR".equalsIgnoreCase(address.getCountryCode())) throw new InvalidRequestException("Delivery is only available in Argentina.");
        String province = ArgentineProvinceMapper.name(address.getProvinceCode());
        require(user.getFirstName(), "first name"); require(user.getLastName(), "last name");
        if (user.getEmail() == null || !EMAIL.matcher(user.getEmail()).matches()) throw new InvalidRequestException("A valid email is required for delivery.");
        if (user.getPhone() == null || !PHONE.matcher(user.getPhone()).matches()) throw new InvalidRequestException("A valid phone is required for delivery.");
        if (user.getDocumentNumber() == null || !DOCUMENT.matcher(user.getDocumentNumber()).matches()) throw new InvalidRequestException("A valid document number is required for delivery.");
        require(address.getStreet(), "street"); require(address.getStreetNumber(), "street number");
        require(address.getLocality(), "locality"); require(address.getPostalCode(), "postal code");
        String name = (user.getFirstName().trim() + " " + user.getLastName().trim()).trim();
        var destination = new ZipnovaGateway.Destination(name, user.getDocumentNumber(), user.getEmail(), user.getPhone(),
                address.getStreet(), address.getStreetNumber(), address.getFloorApartment(), address.getLocality(),
                province, address.getPostalCode());
        var snapshot = new DeliveryAddressSnapshot(name, user.getDocumentNumber(), user.getEmail(), user.getPhone(),
                address.getStreet(), address.getStreetNumber(), address.getFloorApartment(), address.getLocality(),
                province, address.getProvinceCode(), address.getPostalCode(), "AR", address.getReference());
        return new Profile(province, destination, snapshot);
    }
    private List<ProductVariant> loadVariants(List<ShippingHashes.ItemQuantity> inputs) {
        Set<Long> ids = new HashSet<>(); int total = 0; List<ProductVariant> result = new ArrayList<>();
        for (var input : inputs.stream().sorted(Comparator.comparing(ShippingHashes.ItemQuantity::variantId)).toList()) {
            if (!ids.add(input.variantId())) throw new InvalidRequestException("A quote cannot contain duplicate variants.");
            total += input.quantity(); if (total > 1000) throw new InvalidRequestException("A quote cannot exceed 1000 product units.");
            ProductVariant variant = variants.findActiveForShippingQuote(input.variantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product variant not found."));
            if (!variant.getProduct().hasCompleteShippingData()) throw new InvalidRequestException("A product is missing shipping data.");
            result.add(variant);
        }
        return result;
    }
    public List<ZipnovaGateway.Item> expandedItems(List<ProductVariant> products, List<ShippingHashes.ItemQuantity> inputs) {
        Map<Long,Integer> qty = inputs.stream().collect(java.util.stream.Collectors.toMap(ShippingHashes.ItemQuantity::variantId, ShippingHashes.ItemQuantity::quantity));
        List<ZipnovaGateway.Item> result = new ArrayList<>();
        for (ProductVariant variant : products) for (int i = 0; i < qty.get(variant.getId()); i++) {
            var p = variant.getProduct(); result.add(new ZipnovaGateway.Item(p.getShippingWeightGrams(), p.getShippingHeightCm(),
                    p.getShippingWidthCm(), p.getShippingLengthCm(), p.getShippingClassificationId().toString(),
                    p.getName(), p.isMustKeepVertical()));
        }
        return List.copyOf(result);
    }
    private java.math.BigDecimal declaredValue(List<ProductVariant> products, List<ShippingHashes.ItemQuantity> inputs) {
        Map<Long,Integer> qty = inputs.stream().collect(java.util.stream.Collectors.toMap(ShippingHashes.ItemQuantity::variantId, ShippingHashes.ItemQuantity::quantity));
        return products.stream().map(v -> v.getProduct().getPrice().multiply(java.math.BigDecimal.valueOf(qty.get(v.getId()))))
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add).setScale(2, java.math.RoundingMode.HALF_UP);
    }
    private String encode(List<String> tags) { try { return json.writeValueAsString(tags); }
        catch (JsonProcessingException error) { throw new IllegalStateException("Quote tags could not be encoded.", error); } }
    private List<String> decodeTags(String tags) { try {
        return json.readValue(tags, json.getTypeFactory().constructCollectionType(List.class, String.class));
    } catch (JsonProcessingException error) { throw new IllegalStateException("Quote tags could not be decoded.", error); } }
    private void require(String value, String field) { if (value == null || value.isBlank()) throw new InvalidRequestException("A delivery " + field + " is required."); }
    private record Profile(String province, ZipnovaGateway.Destination destination, DeliveryAddressSnapshot snapshot) {}
    public record ValidatedQuote(ShippingQuote quote, DeliveryAddressSnapshot address) {}
}
