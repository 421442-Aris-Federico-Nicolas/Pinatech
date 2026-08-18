package com.computerstore.payment.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.computerstore.common.exception.AuthenticationFailureException;
import com.computerstore.payment.config.MercadoPagoEnvironment;
import com.computerstore.payment.config.MercadoPagoProperties;
import org.junit.jupiter.api.Test;

class MercadoPagoSignatureValidatorTest {

    private static final String SECRET = "webhook-secret";
    private final MercadoPagoSignatureValidator validator = new MercadoPagoSignatureValidator(properties());

    @Test
    void acceptsTheMercadoPagoSignedManifest() throws Exception {
        String paymentId = "123456";
        String requestId = "request-1";
        String timestamp = "1710000000";
        String manifest = "id:" + paymentId + ";request-id:" + requestId + ";ts:" + timestamp + ";";
        String signature = "ts=" + timestamp + ",v1=" + hmac(manifest);

        assertDoesNotThrow(() -> validator.validate(paymentId, requestId, signature));
    }

    @Test
    void rejectsTamperedNotifications() {
        assertThrows(AuthenticationFailureException.class,
                () -> validator.validate("123456", "request-1", "ts=1710000000,v1=deadbeef"));
    }

    private String hmac(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    private MercadoPagoProperties properties() {
        return new MercadoPagoProperties(
                true,
                MercadoPagoEnvironment.SANDBOX,
                "access-token",
                SECRET,
                "99",
                URI.create("https://store.example"),
                Duration.ofSeconds(1),
                Duration.ofSeconds(2));
    }
}
