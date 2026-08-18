package com.computerstore.payment.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.computerstore.common.exception.AuthenticationFailureException;
import com.computerstore.payment.config.MercadoPagoProperties;
import org.springframework.stereotype.Component;

@Component
public class MercadoPagoSignatureValidator {

    private final MercadoPagoProperties properties;
    private final Clock clock;

    public MercadoPagoSignatureValidator(MercadoPagoProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public void validate(String dataId, String requestId, String signature) {
        properties.requireEnabled();
        if (blank(dataId) || blank(requestId) || blank(signature)) {
            throw invalid();
        }
        Map<String, String> parts = java.util.Arrays.stream(signature.split(","))
                .map(String::trim)
                .filter(part -> part.contains("="))
                .map(part -> part.split("=", 2))
                .collect(Collectors.toMap(part -> part[0], part -> part[1], (left, right) -> right));
        String timestamp = parts.get("ts");
        String suppliedHash = parts.get("v1");
        if (blank(timestamp) || blank(suppliedHash)) {
            throw invalid();
        }
        long timestampSeconds;
        try {
            timestampSeconds = Long.parseLong(timestamp);
        } catch (NumberFormatException exception) {
            throw invalid();
        }
        long age = Math.abs(Instant.now(clock).getEpochSecond() - timestampSeconds);
        if (age > properties.webhookTimestampTolerance().toSeconds()) {
            throw invalid();
        }
        String manifest = "id:" + dataId.toLowerCase() + ";request-id:" + requestId + ";ts:" + timestamp + ";";
        String expectedHash = hmac(manifest, properties.webhookSecret());
        if (!MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.US_ASCII),
                suppliedHash.toLowerCase().getBytes(StandardCharsets.US_ASCII))) {
            throw invalid();
        }
    }

    private String hmac(String value, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("HMAC-SHA256 is not available.", exception);
        }
    }

    private AuthenticationFailureException invalid() {
        return new AuthenticationFailureException("Invalid Mercado Pago webhook signature.");
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
