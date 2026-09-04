package com.computerstore.email;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    public void sendAccountAction(String recipient, String firstName, AccountActionPurpose purpose, String rawToken) {
        String route = switch (purpose) {
            case EMAIL_VERIFICATION -> "/verify-email";
            case PASSWORD_RESET -> "/reset-password";
            case EMAIL_CHANGE -> "/confirm-email-change";
        };
        String actionUrl = accountActionUrl(route, rawToken);
        EmailContent content = contentFor(purpose, firstName, actionUrl);
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
    public void sendEmailChangedNotice(String previousEmail, String firstName, String newEmail) {
        EmailContent content = contentForEmailChanged(firstName, newEmail);
        afterCommit("EMAIL_CHANGED_NOTICE", () -> send(previousEmail, content));
    }

    @Override
    public void sendOrderEvent(UUID idempotencyKey, String recipient, String customerName,
                               OrderEmailEventType eventType, Long orderId, String rejectionReason) {
        if (!enabled) {
            LOGGER.info("Transactional email disabled; completed outbox event={}", eventType);
            return;
        }
        String orderUrl = UriComponentsBuilder.fromUriString(storefrontBaseUrl)
                .path("/orders").queryParam("order", orderId).build().encode().toUriString();
        send(recipient, contentForOrderEvent(customerName, eventType, orderId, rejectionReason, orderUrl),
                idempotencyKey);
    }

    @Override
    public void sendSellerOrderEvent(UUID idempotencyKey, String recipient, OrderEmailEventType eventType,
                                     SellerOrderSnapshot snapshot) {
        if (!enabled) {
            LOGGER.info("Transactional email disabled; completed outbox event={}", eventType);
            return;
        }
        String adminUrl = UriComponentsBuilder.fromUriString(storefrontBaseUrl)
                .path("/admin").queryParam("section", "sales").queryParam("order", snapshot.orderId())
                .build().encode().toUriString();
        send(recipient, contentForSellerOrderEvent(eventType, snapshot, adminUrl), idempotencyKey);
    }

    @Override
    public void sendShipmentTracking(UUID idempotencyKey, String recipient, String customerName, Long orderId,
                                     ShipmentTrackingSnapshot snapshot) {
        if (!enabled) { LOGGER.info("Transactional email disabled; completed outbox event={}",
                OrderEmailEventType.SHIPMENT_TRACKING_AVAILABLE); return; }
        String orderUrl = UriComponentsBuilder.fromUriString(storefrontBaseUrl).path("/orders")
                .queryParam("order", orderId).build().encode().toUriString();
        String detail = "Transportista: " + display(snapshot.carrier()) + "\nCodigo de seguimiento: "
                + display(snapshot.code()) + "\nEntrega estimada: " + instant(snapshot.estimatedDeliveryAt());
        send(recipient, renderBrandedEmail(new EmailTemplate(
                "Tu envio ya tiene seguimiento", "Ya podes seguir el envio de tu pedido #" + orderId + ".",
                "Seguimiento disponible", greeting(customerName),
                List.of("La documentacion de tu envio esta lista.", detail), null,
                "Ver mi pedido", orderUrl,
                "El seguimiento seguro y actualizado esta disponible desde tu pedido Pinatech."), emailLogoUrl), idempotencyKey);
    }

    EmailContent contentFor(AccountActionPurpose purpose, String firstName, String actionUrl) {
        return switch (purpose) {
            case EMAIL_VERIFICATION -> renderEmailVerification(firstName, actionUrl, emailLogoUrl);
            case PASSWORD_RESET -> renderBrandedEmail(new EmailTemplate(
                    "Restablece tu contrasena de Pinatech",
                    "Usa este enlace seguro para restablecer tu contrasena de Pinatech.",
                    "Restablece tu contrasena",
                    greeting(firstName),
                    List.of("Recibimos una solicitud para restablecer la contrasena de tu cuenta Pinatech."),
                    null,
                    "Restablecer contrasena",
                    actionUrl,
                    "Este enlace es de un solo uso y vence pronto. Si no solicitaste el cambio, podes ignorar este email."),
                    emailLogoUrl);
            case EMAIL_CHANGE -> renderBrandedEmail(new EmailTemplate(
                    "Confirma tu nuevo email de Pinatech",
                    "Confirma la nueva direccion de email de tu cuenta Pinatech.",
                    "Confirma tu nuevo email",
                    greeting(firstName),
                    List.of("Usa el siguiente boton para confirmar esta direccion como el nuevo email de tu cuenta."),
                    null,
                    "Confirmar nuevo email",
                    actionUrl,
                    "Este enlace es de un solo uso. Si no solicitaste este cambio, no confirmes el email y contactanos."),
                    emailLogoUrl);
        };
    }

    EmailContent contentForEmailChanged(String firstName, String newEmail) {
        return renderBrandedEmail(new EmailTemplate(
                "Tu email de Pinatech fue actualizado",
                "La direccion de email de tu cuenta Pinatech fue actualizada.",
                "Email actualizado",
                greeting(firstName),
                List.of("La direccion de email asociada a tu cuenta fue actualizada correctamente."),
                new EmailCallout("Nuevo email", newEmail),
                null,
                null,
                "Si no realizaste este cambio, contactanos de inmediato para proteger tu cuenta."),
                emailLogoUrl);
    }

    EmailContent contentForOrderEvent(String customerName, OrderEmailEventType eventType, Long orderId,
                                      String rejectionReason, String orderUrl) {
        String orderNumber = "#" + orderId;
        EmailTemplate template = switch (eventType) {
            case ORDER_CREATED -> new EmailTemplate(
                    "Recibimos tu pedido Pinatech",
                    "Tu pedido " + orderNumber + " fue registrado correctamente.",
                    "Recibimos tu pedido",
                    greeting(customerName),
                    List.of("Tu pedido " + orderNumber + " fue registrado correctamente. Podes consultar su estado desde tu cuenta."),
                    null,
                    "Ver mi pedido",
                    orderUrl,
                    "El registro del pedido no acredita el pago. Te avisaremos cuando tengamos una novedad." );
            case PAYMENT_APPROVED -> new EmailTemplate(
                    "Pago aprobado",
                    "Aprobamos el pago de tu pedido " + orderNumber + ".",
                    "Pago aprobado",
                    greeting(customerName),
                    List.of("Aprobamos el pago de tu pedido " + orderNumber + ". Ya podemos continuar con la preparacion."),
                    null,
                    "Ver mi pedido",
                    orderUrl,
                    "Podes seguir el estado del pedido desde tu cuenta Pinatech.");
            case BANK_TRANSFER_REJECTED -> new EmailTemplate(
                    "Comprobante de transferencia rechazado",
                    "No pudimos aprobar el comprobante de tu pedido " + orderNumber + ".",
                    "No pudimos aprobar el comprobante",
                    greeting(customerName),
                    List.of("Revisamos la transferencia del pedido " + orderNumber + " y el comprobante fue rechazado."),
                    new EmailCallout("Motivo", rejectionReason == null ? "No informado" : rejectionReason),
                    "Ver mi pedido",
                    orderUrl,
                    "El pedido fue cancelado y el stock reservado quedo liberado. No realices otra transferencia para este pedido.");
            case ORDER_DELIVERED -> new EmailTemplate(
                    "Pedido entregado",
                    "Registramos la entrega de tu pedido " + orderNumber + ".",
                    "Pedido entregado",
                    greeting(customerName),
                    List.of("Registramos la entrega de tu pedido " + orderNumber + ". Gracias por comprar en Pinatech."),
                    null,
                    "Ver mi pedido",
                    orderUrl,
                    "Conserva este email como confirmacion de la entrega.");
            case SHIPMENT_TRACKING_AVAILABLE -> new EmailTemplate(
                    "Tu envio ya tiene seguimiento", "Ya podes seguir el envio de tu pedido " + orderNumber + ".",
                    "Seguimiento disponible", greeting(customerName),
                    List.of("La documentacion de tu envio esta lista. Consulta el codigo de seguimiento desde tu pedido."),
                    null, "Ver mi pedido", orderUrl, "Segui siempre tu envio desde tu cuenta Pinatech.");
            case SELLER_ORDER_CREATED, SELLER_PAYMENT_APPROVED ->
                    throw new IllegalArgumentException("Seller events require a seller order snapshot.");
        };
        return renderBrandedEmail(template, emailLogoUrl);
    }

    EmailContent contentForSellerOrderEvent(OrderEmailEventType eventType, SellerOrderSnapshot snapshot,
                                             String adminUrl) {
        String orderNumber = "#" + snapshot.orderId();
        String subject;
        String preheader;
        String title;
        String notice;
        if (eventType == OrderEmailEventType.SELLER_ORDER_CREATED) {
            subject = "Nuevo pedido Pinatech " + orderNumber;
            preheader = "Se registro el pedido " + orderNumber + ".";
            title = "Nuevo pedido recibido";
            notice = "Este detalle es una instantanea del momento en que se creo el pedido.";
        } else if (eventType == OrderEmailEventType.SELLER_PAYMENT_APPROVED) {
            subject = "Pago aprobado para el pedido " + orderNumber;
            preheader = "Se aprobo el pago del pedido " + orderNumber + ".";
            title = "Pago aprobado";
            notice = "Este detalle es una instantanea del momento en que se aprobo el pago.";
        } else {
            throw new IllegalArgumentException("A seller email requires a seller event type.");
        }

        List<String> paragraphs = new ArrayList<>();
        paragraphs.add("Pedido: " + orderNumber
                + "\nFecha del pedido: " + instant(snapshot.orderDate())
                + "\nFecha del evento: " + instant(snapshot.eventDate())
                + "\nEstado: " + display(snapshot.orderStatus()));
        paragraphs.add("Pago\nMetodo: " + display(snapshot.paymentMethod())
                + "\nEstado: " + display(snapshot.paymentStatus()));
        paragraphs.add("Importes\nMoneda: " + display(snapshot.currency())
                + "\nSubtotal: " + amount(snapshot.subtotal())
                 + "\nDescuento: " + amount(snapshot.discount())
                 + "\nRecargo: " + amount(snapshot.surcharge())
                 + "\nEnvio: " + amount(snapshot.shipping())
                 + "\nTotal: " + amount(snapshot.total()));
        paragraphs.add("Cliente\nNombre: " + display(snapshot.customerName())
                + "\nEmail: " + display(snapshot.customerEmail())
                + "\nTelefono: " + display(snapshot.customerPhone()));
        paragraphs.add(fulfillmentDetails(snapshot));
        paragraphs.add(itemDetails(snapshot.items()));

        return renderBrandedEmail(new EmailTemplate(
                subject, preheader, title, "Hola equipo de ventas", paragraphs, null,
                "Abrir venta en administracion", adminUrl, notice), emailLogoUrl);
    }

    private static String fulfillmentDetails(SellerOrderSnapshot snapshot) {
        StringBuilder details = new StringBuilder("Entrega")
                .append("\nMetodo de fulfillment: ").append(display(snapshot.fulfillmentMethod()))
                .append("\nEstado de fulfillment: ").append(display(snapshot.fulfillmentStatus()))
                .append("\nMetodo de entrega: ").append(display(snapshot.deliveryMethod()));
        SellerOrderSnapshot.Pickup pickup = snapshot.pickup();
        SellerOrderSnapshot.Delivery delivery = snapshot.delivery();
        if (delivery != null) {
            String address = display(delivery.street()) + " " + display(delivery.streetNumber());
            if (delivery.floorApartment() != null && !delivery.floorApartment().isBlank()) {
                address += ", " + delivery.floorApartment();
            }
            return details.append("\nDestinatario: ").append(display(delivery.recipientName()))
                    .append("\nDireccion: ").append(address)
                    .append("\nLocalidad: ").append(display(delivery.locality()))
                    .append("\nProvincia: ").append(display(delivery.province()))
                    .append("\nCodigo postal: ").append(display(delivery.postalCode()))
                    .append("\nReferencia: ").append(display(delivery.reference()))
                    .toString();
        }
        if (pickup == null) return details.append("\nRetiro: No aplica").toString();
        return details.append("\nRetiro - codigo: ").append(display(pickup.code()))
                .append("\nRetiro - nombre: ").append(display(pickup.name()))
                .append("\nRetiro - direccion: ").append(display(String.join(", ", pickup.addressLines())))
                .append("\nRetiro - localidad: ").append(display(pickup.locality()))
                .append("\nRetiro - provincia: ").append(display(pickup.provinceCode()))
                .append("\nRetiro - codigo postal: ").append(display(pickup.postalCode()))
                .append("\nRetiro - instrucciones: ").append(display(pickup.instructions()))
                .append("\nRetiro - horarios: ").append(display(pickup.hours()))
                .toString();
    }

    private static String itemDetails(List<SellerOrderSnapshot.Item> items) {
        StringBuilder details = new StringBuilder("Productos");
        for (int index = 0; index < items.size(); index++) {
            SellerOrderSnapshot.Item item = items.get(index);
            String color = display(item.color());
            if (item.colorHex() != null && !item.colorHex().isBlank()) color += " (" + item.colorHex() + ")";
            details.append("\n").append(index + 1).append(". ").append(display(item.product()))
                    .append(" | Color: ").append(color)
                    .append(" | Cantidad: ").append(item.quantity())
                    .append(" | Unitario: ").append(amount(item.unitPrice()))
                    .append(" | Subtotal: ").append(amount(item.subtotal()));
        }
        if (items.isEmpty()) details.append("\nSin productos informados");
        return details.toString();
    }

    private static String amount(java.math.BigDecimal value) {
        return value == null ? "No informado" : value.toPlainString();
    }

    private static String instant(java.time.Instant value) {
        return value == null ? "No informada" : DateTimeFormatter.ISO_INSTANT.format(value);
    }

    private static String display(String value) {
        return value == null || value.isBlank() ? "No informado" : value;
    }

    static EmailContent renderEmailVerification(String firstName, String actionUrl, String logoUrl) {
        return renderBrandedEmail(new EmailTemplate(
                "Verifica tu email de Pinatech",
                "Activa tu cuenta Pinatech verificando tu direccion de email.",
                "Verifica tu email",
                greeting(firstName),
                List.of("Gracias por registrarte en Pinatech. Verifica tu email para activar tu cuenta."),
                null,
                "Verificar mi email",
                actionUrl,
                "Este enlace es de un solo uso. Si no creaste una cuenta en Pinatech, podes ignorar este email."),
                logoUrl);
    }

    private static EmailContent renderBrandedEmail(EmailTemplate template, String logoUrl) {
        String safeLogoUrl = escapeHtml(logoUrl == null ? "" : logoUrl);
        String safePreheader = escapeHtml(template.preheader());
        String safeTitle = escapeHtml(template.title());
        String safeGreeting = escapeHtml(template.greeting());
        StringBuilder text = new StringBuilder(template.greeting()).append(",\n\n");
        StringBuilder paragraphs = new StringBuilder();
        for (String paragraph : template.paragraphs()) {
            text.append(paragraph).append("\n\n");
            paragraphs.append("<p style=\"margin:0 0 16px;font-size:16px;line-height:25px;color:#33465f;\">")
                    .append(escapeHtml(paragraph).replace("\n", "<br>")).append("</p>");
        }
        String calloutHtml = "";
        if (template.callout() != null) {
            text.append(template.callout().label()).append(": ").append(template.callout().value()).append("\n\n");
            calloutHtml = """
                    <div style="margin:8px 0 24px;padding:16px 18px;background-color:#fff3e8;border-left:4px solid #f97316;">
                      <p style="margin:0 0 4px;font-size:12px;line-height:18px;font-weight:700;text-transform:uppercase;letter-spacing:0.5px;color:#9a4b0b;">%s</p>
                      <p style="margin:0;font-size:15px;line-height:23px;color:#33465f;">%s</p>
                    </div>
                    """.formatted(escapeHtml(template.callout().label()), escapeHtml(template.callout().value()));
        }
        String actionHtml = "";
        String fallbackHtml = "";
        if (template.actionLabel() != null && template.actionUrl() != null) {
            text.append(template.actionLabel()).append(":\n").append(template.actionUrl()).append("\n\n");
            String safeActionLabel = escapeHtml(template.actionLabel());
            String safeActionUrl = escapeHtml(template.actionUrl());
            actionHtml = """
                    <table role="presentation" cellspacing="0" cellpadding="0" border="0" style="border-collapse:separate;">
                      <tr>
                        <td align="center" bgcolor="#f97316" style="border-radius:6px;background-color:#f97316;">
                          <a href="%s" style="display:inline-block;padding:14px 24px;font-family:Arial,Helvetica,sans-serif;font-size:16px;line-height:20px;font-weight:700;color:#ffffff;text-decoration:none;border:1px solid #f97316;border-radius:6px;">%s</a>
                        </td>
                      </tr>
                    </table>
                    """.formatted(safeActionUrl, safeActionLabel);
            fallbackHtml = """
                    <tr>
                      <td style="padding:8px 32px 28px;font-family:Arial,Helvetica,sans-serif;">
                        <p style="margin:0 0 8px;font-size:13px;line-height:20px;color:#607086;">Si el boton no funciona, abri este enlace:</p>
                        <p style="margin:0;font-size:13px;line-height:20px;word-break:break-all;"><a href="%s" style="color:#087f8c;text-decoration:underline;word-break:break-all;">%s</a></p>
                      </td>
                    </tr>
                    """.formatted(safeActionUrl, safeActionUrl);
        }
        text.append(template.notice());
        String html = """
                <!doctype html>
                <html lang="es">
                  <head>
                    <meta charset="utf-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <title>%s</title>
                  </head>
                  <body style="margin:0;padding:0;background-color:#fff8e7;color:#0b1f3a;">
                    <div style="display:none;max-height:0;max-width:0;overflow:hidden;opacity:0;color:transparent;mso-hide:all;">
                      %s
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
                                <h1 style="margin:0 0 20px;font-size:28px;line-height:36px;color:#0b1f3a;font-weight:700;">%s</h1>
                                <p style="margin:0 0 16px;font-size:16px;line-height:25px;color:#0b1f3a;">%s,</p>
                                %s
                                %s
                                %s
                              </td>
                            </tr>
                            %s
                            <tr>
                              <td style="padding:22px 32px;background-color:#e9fbfd;border-top:1px solid #b9edf2;font-family:Arial,Helvetica,sans-serif;">
                                <p style="margin:0;font-size:13px;line-height:21px;color:#33465f;">%s</p>
                              </td>
                            </tr>
                          </table>
                        </td>
                      </tr>
                    </table>
                  </body>
                </html>
                """.formatted(escapeHtml(template.subject()), safePreheader, safeLogoUrl, safeTitle,
                safeGreeting, paragraphs, calloutHtml, actionHtml, fallbackHtml, escapeHtml(template.notice()));
        return new EmailContent(template.subject(), text.toString(), html);
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
        send(recipient, content, null);
    }

    private void send(String recipient, EmailContent content, UUID idempotencyKey) {
        try {
            String body = buildPayload(recipient, content);
            HttpRequest.Builder builder = HttpRequest.newBuilder(RESEND_ENDPOINT)
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            if (idempotencyKey != null) builder.header("Idempotency-Key", idempotencyKey.toString());
            HttpRequest request = builder.build();
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

    private static String greeting(String name) {
        return name == null || name.isBlank() ? "Hola" : "Hola " + name.trim();
    }

    private record EmailTemplate(String subject, String preheader, String title, String greeting,
                                 List<String> paragraphs, EmailCallout callout, String actionLabel,
                                 String actionUrl, String notice) {
    }

    private record EmailCallout(String label, String value) {
    }

    record EmailContent(String subject, String text, String html) {
    }
}
