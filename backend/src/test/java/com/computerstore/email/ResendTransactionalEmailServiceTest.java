package com.computerstore.email;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.computerstore.user.domain.AccountActionPurpose;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ResendTransactionalEmailServiceTest {

    @Test
    void enabledEmailRequiresCompleteProviderConfiguration() {
        assertThrows(IllegalStateException.class, () ->
                new ResendTransactionalEmailService(true, "", "Pinatech <accounts@example.com>",
                        "https://store.example.com", "https://cdn.example.com/logo.png", new ObjectMapper()));
        assertThrows(IllegalStateException.class, () ->
                new ResendTransactionalEmailService(true, "key", "Pinatech <accounts@example.com>",
                        "not-a-url", "https://cdn.example.com/logo.png", new ObjectMapper()));
        assertThrows(IllegalStateException.class, () ->
                new ResendTransactionalEmailService(true, "key", "Pinatech <accounts@example.com>",
                        "https://store.example.com", "", new ObjectMapper()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://cdn.example.com/logo.png",
            "/logo.png",
            "https:logo.png",
            "https:/logo.png",
            "https://invalid_host/logo.png",
            "https://user:secret@cdn.example.com/logo.png",
            "https://cdn.example.com/logo.png?version=1",
            "https://cdn.example.com/logo.png#brand"
    })
    void enabledEmailRejectsInvalidLogoUrl(String logoUrl) {
        assertThrows(IllegalStateException.class, () ->
                new ResendTransactionalEmailService(true, "key", "Pinatech <accounts@example.com>",
                        "http://localhost:4200", logoUrl, new ObjectMapper()));
    }

    @Test
    void disabledEmailDoesNotAttemptExternalDelivery() {
        ResendTransactionalEmailService service = new ResendTransactionalEmailService(
                false, "", "", "https://store.example.com", "", new ObjectMapper());

        assertDoesNotThrow(() -> service.sendEmailVerification(
                "user@example.com", "Ana", "raw-token"));
        assertDoesNotThrow(() -> service.sendAccountAction(
                "user@example.com", AccountActionPurpose.EMAIL_VERIFICATION, "raw-token"));
        assertDoesNotThrow(() -> service.sendEmailChangedNotice(
                "old@example.com", "new@example.com"));
    }

    @Test
    void verificationContentEscapesNameAndActionUrlInHtml() {
        String actionUrl = "https://store.example.com/verify-email?next=<home>&mode=\"fast\"#token=a'b";

        ResendTransactionalEmailService.EmailContent content =
                ResendTransactionalEmailService.renderEmailVerification(
                        "Ana & <Admin> \"QA\"", actionUrl,
                        "https://store.example.com/pinatech-favicon.png");

        assertTrue(content.html().contains("Hola Ana &amp; &lt;Admin&gt; &quot;QA&quot;,"));
        assertFalse(content.html().contains("Hola Ana & <Admin> \"QA\","));
        assertTrue(content.html().contains(
                "https://store.example.com/verify-email?next=&lt;home&gt;&amp;mode=&quot;fast&quot;#token=a&#39;b"));
        assertFalse(content.html().contains(actionUrl));
        assertTrue(content.text().startsWith("Hola Ana & <Admin> \"QA\",\n\n"));
        assertTrue(content.text().contains(actionUrl));
    }

    @Test
    void verificationContentIncludesBrandingCtaFallbackAndSecurityNotice() {
        String actionUrl = "https://store.example.com/verify-email#token=raw-token";
        ResendTransactionalEmailService.EmailContent content =
                ResendTransactionalEmailService.renderEmailVerification(
                        "Ana", actionUrl, "https://store.example.com/pinatech-favicon.png");

        assertEquals("Verifica tu email de Pinatech", content.subject());
        assertTrue(content.html().contains("display:none;max-height:0"));
        assertTrue(content.html().contains("role=\"presentation\""));
        assertTrue(content.html().contains("Verificar mi email"));
        assertTrue(content.html().contains(">" + actionUrl + "</a>"));
        assertTrue(content.html().contains("#0b1f3a"));
        assertTrue(content.html().contains("#f97316"));
        assertTrue(content.html().contains("#22d3ee"));
        assertTrue(content.html().contains("#fff8e7"));
        assertTrue(content.html().contains("Este enlace es de un solo uso."));
        assertTrue(content.html().contains("podes ignorar este email."));
        assertTrue(content.text().contains("Verificar mi email:\n" + actionUrl));
        assertTrue(content.text().contains(
                "Este enlace es de un solo uso. Si no creaste una cuenta en Pinatech, "
                        + "podes ignorar este email."));
    }

    @Test
    void verificationLogoUsesExactConfiguredUrlIndependentlyOfStorefront() {
        String logoUrl = "https://cdn.example.com/assets/pinatech-logo.png";
        ResendTransactionalEmailService service = new ResendTransactionalEmailService(
                true, "key", "Pinatech <accounts@example.com>", "http://localhost:4200/app",
                logoUrl, new ObjectMapper());

        String actionUrl = service.accountActionUrl("/verify-email", "raw/token&value");
        ResendTransactionalEmailService.EmailContent content = service.contentFor(
                AccountActionPurpose.EMAIL_VERIFICATION, actionUrl);

        assertTrue(actionUrl.startsWith("http://localhost:4200/app/verify-email"));
        assertTrue(actionUrl.endsWith("#token=raw/token%26value"));
        assertTrue(content.html().contains("src=\"" + logoUrl + "\""));
        assertFalse(content.html().contains("src=\"http://localhost:4200"));
        assertFalse(content.html().contains("cid:"));
    }

    @Test
    void verificationContentEscapesConfiguredLogoUrlInHtml() {
        String logoUrl = "https://cdn.example.com/assets/pinatech&brand's-logo.png";
        ResendTransactionalEmailService service = new ResendTransactionalEmailService(
                true, "key", "Pinatech <accounts@example.com>", "https://store.example.com",
                logoUrl, new ObjectMapper());

        ResendTransactionalEmailService.EmailContent content = service.contentFor(
                AccountActionPurpose.EMAIL_VERIFICATION,
                "https://store.example.com/verify-email#token=raw-token");

        assertTrue(content.html().contains(
                "src=\"https://cdn.example.com/assets/pinatech&amp;brand&#39;s-logo.png\""));
        assertFalse(content.html().contains("src=\"" + logoUrl + "\""));
    }

    @Test
    void verificationPayloadContainsPublicLogoWithoutAttachmentsOrCredentials() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        String logoUrl = "https://cdn.example.com/assets/pinatech-logo.png";
        ResendTransactionalEmailService service = new ResendTransactionalEmailService(
                true, "secret-provider-key", "Pinatech <accounts@example.com>",
                "https://store.example.com", logoUrl, objectMapper);
        ResendTransactionalEmailService.EmailContent content =
                ResendTransactionalEmailService.renderEmailVerification(
                        "Ana", "https://store.example.com/verify-email#token=raw-token", logoUrl);

        String payload = service.buildPayload("ana@example.com", content);
        JsonNode json = objectMapper.readTree(payload);

        assertEquals("Pinatech <accounts@example.com>", json.path("from").asText());
        assertEquals("ana@example.com", json.path("to").get(0).asText());
        assertTrue(json.path("html").asText().contains("src=\"" + logoUrl + "\""));
        assertTrue(json.path("text").asText().contains("Verificar mi email"));
        assertFalse(json.has("attachments"));
        assertFalse(payload.contains("secret-provider-key"));
    }

    @Test
    void passwordResetAndEmailChangeContentRemainUnchanged() {
        ResendTransactionalEmailService service = new ResendTransactionalEmailService(
                false, "", "", "https://store.example.com", "", new ObjectMapper());
        String actionUrl = "https://store.example.com/action#token=raw&amp;unsafe";

        assertEquals(new ResendTransactionalEmailService.EmailContent(
                        "Restablece tu contrasena de Pinatech",
                        "Restablece tu contrasena ingresando en: " + actionUrl,
                        "<p>Recibimos una solicitud para restablecer tu contrasena.</p><p><a href=\""
                                + "https://store.example.com/action#token=raw&amp;amp;unsafe"
                                + "\">Restablecer contrasena</a></p>"),
                service.contentFor(AccountActionPurpose.PASSWORD_RESET, actionUrl));
        assertEquals(new ResendTransactionalEmailService.EmailContent(
                        "Confirma tu nuevo email de Pinatech",
                        "Confirma tu nuevo email ingresando en: " + actionUrl,
                        "<p>Confirma este email como el nuevo email de tu cuenta.</p><p><a href=\""
                                + "https://store.example.com/action#token=raw&amp;amp;unsafe"
                                + "\">Confirmar nuevo email</a></p>"),
                service.contentFor(AccountActionPurpose.EMAIL_CHANGE, actionUrl));
    }
}
