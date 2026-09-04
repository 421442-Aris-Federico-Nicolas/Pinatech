package com.computerstore.shipping.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

import com.computerstore.catalog.domain.ProductVariant;
import com.computerstore.order.dto.CreateOrderRequest;
import com.computerstore.shipping.dto.ShippingQuoteRequest;
import com.computerstore.user.domain.UserAccount;
import com.computerstore.user.domain.UserAddress;

public final class ShippingHashes {
    private ShippingHashes() {}

    public static String cart(List<ItemQuantity> inputs, List<ProductVariant> variants) {
        var quantities = inputs.stream().collect(java.util.stream.Collectors.toMap(ItemQuantity::variantId,
                ItemQuantity::quantity));
        String canonical = variants.stream().sorted(Comparator.comparing(ProductVariant::getId)).map(variant -> {
            var product = variant.getProduct();
            return join(variant.getId(), quantities.get(variant.getId()), product.getPrice(),
                    product.getShippingWeightGrams(), product.getShippingHeightCm(), product.getShippingWidthCm(),
                    product.getShippingLengthCm(), product.getShippingClassificationId(), product.isMustKeepVertical());
        }).reduce((left, right) -> left + "|" + right).orElse("");
        return sha256(canonical);
    }

    public static String profile(UserAccount user, UserAddress address, String province) {
        return sha256(join(user.getFirstName(), user.getLastName(), user.getEmail(), user.getPhone(),
                user.getDocumentNumber(), address.getStreet(), address.getStreetNumber(), address.getFloorApartment(),
                address.getLocality(), province, address.getProvinceCode(), address.getPostalCode(),
                address.getCountryCode(), address.getReference()));
    }

    public static ItemQuantity item(ShippingQuoteRequest.Item item) {
        return new ItemQuantity(item.variantId(), item.quantity());
    }
    public static ItemQuantity item(CreateOrderRequest.Item item) {
        return new ItemQuantity(item.variantId(), item.quantity());
    }

    public record ItemQuantity(Long variantId, Integer quantity) {}

    private static String join(Object... values) {
        StringBuilder result = new StringBuilder();
        for (Object value : values) {
            String text = value == null ? "" : value.toString().trim();
            result.append(text.length()).append(':').append(text);
        }
        return result.toString();
    }
    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException("SHA-256 is unavailable.", error); }
    }
}
