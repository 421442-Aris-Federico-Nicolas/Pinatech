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
    private final String emailLogoUrl;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ResendTransactionalEmailService(
            @Value("${app.email.resend.enabled:false}") boolean enabled,
            @Value("${app.email.resend.api-key:}") String apiKey,
            @Value("${app.email.resend.from:}") String from,
            @Value("${app.storefront.base-url:http://localhost:4200}") String storefrontBaseUrl,
            @Value("${app.email.logo-url:}") String emailLogoUrl,
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
            validateEmailLogoUrl(emailLogoUrl);
        }
        this.enabled = enabled;
        this.apiKey = apiKey;
        this.from = from;
        this.storefrontBaseUrl = storefrontBaseUrl;
        this.emailLogoUrl = emailLogoUrl;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Override
    public void sendEmailVerification(String recipient, String firstName, String rawToken) {
        String actionUrl = accountActionUrl("/verify-email", rawToken);
        EmailContent content = renderEmailVerification(firstName, actionUrl, emailLogoUrl);
        afterCommit(AccountActionPurpose.EMAIL_VERIFICATION.name(), () -> send(recipient, content));
    }

    @Override
    public void sendAccountAction(String recipient, AccountActionPurpose purpose, String rawToken) {
        String route = switch (purpose) {
            case EMAIL_VERIFICATION -> "/verify-email";
            case PASSWORD_RESET -> "/reset-password";
            case EMAIL_CHANGE -> "/confirm-email-change";
        };
        String actionUrl = accountActionUrl(route, rawToken);
        EmailContent content = contentFor(purpose, actionUrl);
        afterCommit(purpose.name(), () -> send(recipient, content));
    }

    String accountActionUrl(String route, String rawToken) {
        return UriComponentsBuilder.fromUriString(storefrontBaseUrl)
                .path(route)
                .build()
                .encode()
                .toUriString()
                + "#token=" + UriUtils.encodeQueryParam(rawToken, StandardCharsets.UTF_8);
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

    EmailContent contentFor(AccountActionPurpose purpose, String actionUrl) {
        String safeUrl = escapeHtml(actionUrl);
        return switch (purpose) {
            case EMAIL_VERIFICATION -> renderEmailVerification("", actionUrl, emailLogoUrl);
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

    static EmailContent renderEmailVerification(String firstName, String actionUrl, String logoUrl) {
        String name = firstName == null ? "" : firstName;
        String textGreeting = name.isBlank() ? "Hola" : "Hola " + name;
        String htmlGreeting = escapeHtml(textGreeting);
        String safeActionUrl = escapeHtml(actionUrl);
        String safeLogoUrl = escapeHtml(logoUrl);
        String text = textGreeting + ",\n\n"
                + "Gracias por registrarte en Pinatech. Verifica tu email para activar tu cuenta.\n\n"
                + "Verificar mi email:\n" + actionUrl + "\n\n"
                + "Este enlace es de un solo uso. Si no creaste una cuenta en Pinatech, "
                + "podes ignorar este email.";
        String html = """
                <!doctype html>
                <html lang="es">
                  <head>
                    <meta charset="utf-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <title>Verifica tu email de Pinatech</title>
                  </head>
                  <body style="margin:0;padding:0;background-color:#fff8e7;color:#0b1f3a;">
                    <div style="display:none;max-height:0;max-width:0;overflow:hidden;opacity:0;color:transparent;mso-hide:all;">
                      Activa tu cuenta Pinatech verificando tu direccion de email.
                    </div>
                    <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="width:100%%;border-collapse:collapse;background-color:#fff8e7;">
                      <tr>
                        <td align="center" style="padding:32px 12px;">
                          <table role="presentation" width="600" cellspacing="0" cellpadding="0" border="0" style="width:100%%;max-width:600px;border-collapse:collapse;background-color:#ffffff;border:1px solid #d8e5e8;">
                            <tr>
                              <td align="center" style="padding:28px 24px;background-color:#0b1f3a;border-bottom:4px solid #f97316;">
                                <img src="%s" width="56" height="56" alt="Pinatech" style="display:block;width:56px;height:56px;border:0;outline:none;text-decoration:none;margin:0 auto 12px;">
                                <div style="font-family:Arial,Helvetica,sans-serif;font-size:26px;line-height:32px;font-weight:700;color:#ffffff;letter-spacing:0.5px;">Pinatech</div>
                                <div style="font-family:Arial,Helvetica,sans-serif;font-size:13px;line-height:20px;color:#22d3ee;">Tecnologia que te conecta</div>
                              </td>
                            </tr>
                            <tr>
                              <td style="padding:36px 32px 20px;font-family:Arial,Helvetica,sans-serif;">
                                <h1 style="margin:0 0 20px;font-size:28px;line-height:36px;color:#0b1f3a;font-weight:700;">Verifica tu email</h1>
                                <p style="margin:0 0 16px;font-size:16px;line-height:25px;color:#0b1f3a;">%s,</p>
                                <p style="margin:0 0 28px;font-size:16px;line-height:25px;color:#33465f;">Gracias por registrarte en Pinatech. Verifica tu email para activar tu cuenta.</p>
                                <table role="presentation" cellspacing="0" cellpadding="0" border="0" style="border-collapse:separate;">
                                  <tr>
                                    <td align="center" bgcolor="#f97316" style="border-radius:6px;background-color:#f97316;">
                                      <a href="%s" style="display:inline-block;padding:14px 24px;font-family:Arial,Helvetica,sans-serif;font-size:16px;line-height:20px;font-weight:700;color:#ffffff;text-decoration:none;border:1px solid #f97316;border-radius:6px;">Verificar mi email</a>
                                    </td>
                                  </tr>
                                </table>
                              </td>
                            </tr>
                            <tr>
                              <td style="padding:8px 32px 28px;font-family:Arial,Helvetica,sans-serif;">
                                <p style="margin:0 0 8px;font-size:13px;line-height:20px;color:#607086;">Si el boton no funciona, abri este enlace:</p>
                                <p style="margin:0;font-size:13px;line-height:20px;word-break:break-all;"><a href="%s" style="color:#087f8c;text-decoration:underline;word-break:break-all;">%s</a></p>
                              </td>
                            </tr>
                            <tr>
                              <td style="padding:22px 32px;background-color:#e9fbfd;border-top:1px solid #b9edf2;font-family:Arial,Helvetica,sans-serif;">
                                <p style="margin:0;font-size:13px;line-height:21px;color:#33465f;">Este enlace es de un solo uso. Si no creaste una cuenta en Pinatech, podes ignorar este email.</p>
                              </td>
                            </tr>
                          </table>
                        </td>
                      </tr>
                    </table>
                  </body>
                </html>
                """.formatted(safeLogoUrl, htmlGreeting, safeActionUrl, safeActionUrl, safeActionUrl);
        return new EmailContent("Verifica tu email de Pinatech", text, html);
    }

    private static void validateEmailLogoUrl(String logoUrl) {
        URI logoUri;
        try {
            logoUri = URI.create(logoUrl);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Email logo URL is invalid.");
        }
        if (!logoUri.isAbsolute()
                || logoUri.isOpaque()
                || !"https".equalsIgnoreCase(logoUri.getScheme())
                || logoUri.getHost() == null
                || logoUri.getHost().isBlank()
                || logoUri.getRawUserInfo() != null
                || logoUri.getRawQuery() != null
                || logoUri.getRawFragment() != null) {
            throw new IllegalStateException(
                    "Email logo URL must be an absolute HTTPS URL with a host and no credentials, query or fragment.");
        }
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
            String body = buildPayload(recipient, content);
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
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Email request was interrupted.", exception);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Email provider is unavailable.", exception);
        }
    }

    String buildPayload(String recipient, EmailContent content) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "from", from,
                    "to", List.of(recipient),
                    "subject", content.subject(),
                    "text", content.text(),
                    "html", content.html()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Email payload could not be encoded.", exception);
        }
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    record EmailContent(String subject, String text, String html) {
    }
}
