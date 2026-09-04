package com.computerstore.shipping.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import com.computerstore.common.exception.ResourceNotFoundException;
import com.computerstore.shipping.config.ZipnovaProperties;
import com.computerstore.shipping.domain.ShippingWebhookInbox;
import com.computerstore.shipping.gateway.*;
import com.computerstore.shipping.repository.ShippingWebhookInboxRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ZipnovaWebhookService {
    private final ZipnovaProperties properties; private final ShippingWebhookInboxRepository inbox;
    private final ShipmentDispatchService shipments; private final ZipnovaGateway gateway; private final Clock clock;
    private final ZipnovaWebhookCompletionService completion;
    public ZipnovaWebhookService(ZipnovaProperties properties, ShippingWebhookInboxRepository inbox,
            ShipmentDispatchService shipments, ZipnovaGateway gateway, Clock clock,
            ZipnovaWebhookCompletionService completion) {
        this.properties = properties; this.inbox = inbox; this.shipments = shipments; this.gateway = gateway;
        this.clock = clock; this.completion = completion;
    }
    public void accept(String suppliedSecret, JsonNode payload) {
        if (!properties.available() || suppliedSecret == null || !MessageDigest.isEqual(
                properties.webhookSecret().getBytes(StandardCharsets.UTF_8), suppliedSecret.getBytes(StandardCharsets.UTF_8))) {
            throw new ResourceNotFoundException("Webhook not found.");
        }
        long providerId = providerId(payload);
        String hash = sha256(providerId + "|" + payload.toString());
        if (inbox.existsByPayloadHash(hash)) return;
        try { inbox.saveAndFlush(new ShippingWebhookInbox(providerId, hash, Instant.now(clock))); }
        catch (DataIntegrityViolationException duplicate) { /* A concurrent duplicate is already queued. */ }
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Instruction> claim() {
        Instant now = Instant.now(clock);
        return inbox.findNextForUpdate(now).flatMap(inbox::findByIdForUpdate)
                .map(item -> new Instruction(item.getId(), item.lease(now), item.getProviderShipmentId()));
    }
    public void process(Instruction item) {
        try {
            var provider = gateway.getShipment(item.providerId());
            shipments.applyWebhook(item.providerId(), provider, gateway.tracking(item.providerId()));
            completion.complete(item, true, null);
        } catch (ShippingProviderException error) { completion.complete(item, false, error.getMessage()); }
    }
    private long providerId(JsonNode payload) {
        if (payload == null || !payload.isObject()) throw new com.computerstore.common.exception.InvalidRequestException("Invalid Zipnova webhook.");
        JsonNode candidate = payload.get("shipment_id");
        if (candidate == null) candidate = payload.get("resource_id");
        if (candidate == null && payload.path("data").isObject()) candidate = payload.path("data").get("id");
        if (candidate == null && payload.path("resource").isObject()) candidate = payload.path("resource").get("id");
        if (candidate == null || !candidate.canConvertToLong() || candidate.longValue() <= 0)
            throw new com.computerstore.common.exception.InvalidRequestException("Invalid Zipnova webhook.");
        return candidate.longValue();
    }
    private String sha256(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception error) { throw new IllegalStateException(error); } }
    public record Instruction(UUID id, UUID token, long providerId) {}
}
