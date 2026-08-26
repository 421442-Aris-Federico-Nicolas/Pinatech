package com.computerstore.email;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.computerstore.user.domain.AccountActionPurpose;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

@Service
public class ResendTransactionalEmailService implements TransactionalEmailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResendTransactionalEmailService.class);
    private static final URI RESEND_ENDPOINT = URI.create("https://api.resend.com/emails");

    private final boolean enabled;
    private final String apiKey;
    private final String from;
    private final String storefrontBaseUrl;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ResendTransactionalEmailService(
            @Value("${app.email.resend.enabled:false}") boolean enabled,
            @Value("${app.email.resend.api-key:}") String apiKey,
            @Value("${app.email.resend.from:}") String from,
            @Value("${app.storefront.base-url:http://localhost:4200}") String storefrontBaseUrl,
            ObjectMapper objectMapper
    ) {
        if (enabled && (apiKey.isBlank() || from.isBlank() || storefrontBaseUrl.isBlank())) {
            throw new IllegalStateException("Resend requires an API key, sender and storefront base URL.");
        }
        if (enabled) {
            URI storefrontUri;
            try {
                storefrontUri = URI.create(storefrontBaseUrl);
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("Storefront base URL is invalid.");
            }
            if (!storefrontUri.isAbsolute()
                    || !("https".equalsIgnoreCase(storefrontUri.getScheme())
                    || "http".equalsIgnoreCase(storefrontUri.getScheme()))) {
                throw new IllegalStateException("Storefront base URL must use HTTP or HTTPS.");
            }
        }
        this.enabled = enabled;
        this.apiKey = apiKey;
        this.from = from;
        this.storefrontBaseUrl = storefrontBaseUrl;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Override
    public void sendAccountAction(String recipient, AccountActionPurpose purpose, String rawToken) {
        String route = switch (purpose) {
            case EMAIL_VERIFICATION -> "/verify-email";
            case PASSWORD_RESET -> "/reset-password";
            case EMAIL_CHANGE -> "/confirm-email-change";
        };
        String actionUrl = UriComponentsBuilder.fromUriString(storefrontBaseUrl)
                .path(route)
                .build()
                .encode()
                .toUriString()
                + "#token=" + UriUtils.encodeQueryParam(rawToken, StandardCharsets.UTF_8);
        EmailContent content = contentFor(purpose, actionUrl);
        afterCommit(purpose.name(), () -> send(recipient, content));
    }

    @Override
    public void sendEmailChangedNotice(String previousEmail, String newEmail) {
        String safeNewEmail = escapeHtml(newEmail);
        EmailContent content = new EmailContent(
                "Tu email de Pinatech fue actualizado",
                "El email de tu cuenta Pinatech fue actualizado a " + newEmail
                        + ". Si no realizaste este cambio, contactanos de inmediato.",
                "<p>El email de tu cuenta Pinatech fue actualizado a <strong>" + safeNewEmail
                        + "</strong>.</p><p>Si no realizaste este cambio, contactanos de inmediato.</p>");
        afterCommit("EMAIL_CHANGED_NOTICE", () -> send(previousEmail, content));
    }

    private EmailContent contentFor(AccountActionPurpose purpose, String actionUrl) {
        String safeUrl = escapeHtml(actionUrl);
        return switch (purpose) {
            case EMAIL_VERIFICATION -> new EmailContent(
                    "Verifica tu email de Pinatech",
                    "Verifica tu email ingresando en: " + actionUrl,
                    "<p>Gracias por registrarte en Pinatech.</p><p><a href=\"" + safeUrl
                            + "\">Verificar mi email</a></p>");
            case PASSWORD_RESET -> new EmailContent(
                    "Restablece tu contrasena de Pinatech",
                    "Restablece tu contrasena ingresando en: " + actionUrl,
                    "<p>Recibimos una solicitud para restablecer tu contrasena.</p><p><a href=\""
                            + safeUrl + "\">Restablecer contrasena</a></p>");
            case EMAIL_CHANGE -> new EmailContent(
                    "Confirma tu nuevo email de Pinatech",
                    "Confirma tu nuevo email ingresando en: " + actionUrl,
                    "<p>Confirma este email como el nuevo email de tu cuenta.</p><p><a href=\""
                            + safeUrl + "\">Confirmar nuevo email</a></p>");
        };
    }

    private void afterCommit(String purpose, Runnable sendOperation) {
        if (!enabled) {
            LOGGER.info("Transactional email disabled; skipped purpose={}", purpose);
            return;
        }
        Runnable safeOperation = () -> {
            try {
                sendOperation.run();
            } catch (RuntimeException exception) {
                LOGGER.error("Transactional email failed purpose={} error={}",
                        purpose, exception.getClass().getSimpleName());
            }
        };
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safeOperation.run();
                }
            });
        } else {
            safeOperation.run();
        }
    }

    private void send(String recipient, EmailContent content) {
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "from", from,
                    "to", List.of(recipient),
                    "subject", content.subject(),
                    "text", content.text(),
                    "html", content.html()));
            HttpRequest request = HttpRequest.newBuilder(RESEND_ENDPOINT)
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Resend returned HTTP " + response.statusCode());
            }
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Email payload could not be encoded.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Email request was interrupted.", exception);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Email provider is unavailable.", exception);
        }
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private record EmailContent(String subject, String text, String html) {
    }
}
