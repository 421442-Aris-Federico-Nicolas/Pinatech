package com.computerstore.payment.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.computerstore.common.exception.AuthenticationFailureException;
import com.computerstore.payment.config.MercadoPagoEnvironment;
import com.computerstore.payment.config.MercadoPagoProperties;
import org.junit.jupiter.api.Test;

class MercadoPagoSignatureValidatorTest {

    private static final String SECRET = "webhook-secret";
    private static final Instant NOW = Instant.parse("2026-08-18T00:00:00Z");
    private final MercadoPagoSignatureValidator validator = new MercadoPagoSignatureValidator(
            properties(), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void acceptsTheMercadoPagoSignedManifestWithinTolerance() throws Exception {
        String timestamp = Long.toString(NOW.minusSeconds(30).getEpochSecond());
        String manifest = "id:123456;request-id:request-1;ts:" + timestamp + ";";
        String signature = "ts=" + timestamp + ",v1=" + hmac(manifest);

        assertDoesNotThrow(() -> validator.validate("123456", "request-1", signature));
    }

    @Test
    void rejectsTamperedNotifications() {
        String timestamp = Long.toString(NOW.getEpochSecond());
        assertThrows(AuthenticationFailureException.class,
                () -> validator.validate("123456", "request-1", "ts=" + timestamp + ",v1=deadbeef"));
    }

    @Test
    void rejectsNonNumericAndReplayedTimestamps() throws Exception {
        assertThrows(AuthenticationFailureException.class,
                () -> validator.validate("123456", "request-1", "ts=not-a-number,v1=deadbeef"));
        String timestamp = Long.toString(NOW.minus(Duration.ofMinutes(6)).getEpochSecond());
        String manifest = "id:123456;request-id:request-1;ts:" + timestamp + ";";
        assertThrows(AuthenticationFailureException.class,
                () -> validator.validate("123456", "request-1", "ts=" + timestamp + ",v1=" + hmac(manifest)));
    }

    private String hmac(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    private MercadoPagoProperties properties() {
        return new MercadoPagoProperties(
                true, MercadoPagoEnvironment.SANDBOX, "TEST-access-token", SECRET, "99",
                URI.create("https://store.example"), Duration.ofSeconds(1), Duration.ofSeconds(2),
                false, Duration.ofMinutes(5), Duration.ofDays(30));
    }
}
