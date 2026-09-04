package com.computerstore.shipping.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.List;
import java.util.UUID;
import com.computerstore.common.exception.ResourceNotFoundException;
import com.computerstore.shipping.config.ZipnovaProperties;
import com.computerstore.shipping.gateway.ZipnovaGateway;
import com.computerstore.shipping.repository.ShippingWebhookInboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ZipnovaWebhookServiceTest {
    @Test
    void duplicateWebhookIsAcknowledgedWithoutASecondInboxRow() throws Exception {
        ShippingWebhookInboxRepository inbox = mock(ShippingWebhookInboxRepository.class);
        when(inbox.existsByPayloadHash(any())).thenReturn(false, true);
        ZipnovaWebhookService service = service(inbox);
        var payload = new ObjectMapper().readTree("{\"shipment_id\":99}");
        service.accept("012345678901234567890123", payload); service.accept("012345678901234567890123", payload);
        verify(inbox, times(1)).saveAndFlush(any());
    }
    @Test
    void secretComparisonRejectsInvalidWebhook() throws Exception {
        assertThrows(ResourceNotFoundException.class, () -> service(mock(ShippingWebhookInboxRepository.class))
                .accept("wrong", new ObjectMapper().readTree("{\"shipment_id\":99}")));
    }

    @Test
    void webhookAppliesProviderStateWhenTrackingLookupFails() {
        ShippingWebhookInboxRepository inbox = mock(ShippingWebhookInboxRepository.class);
        ShipmentDispatchService shipments = mock(ShipmentDispatchService.class);
        ZipnovaGateway gateway = mock(ZipnovaGateway.class);
        ZipnovaWebhookCompletionService completion = mock(ZipnovaWebhookCompletionService.class);
        var properties = properties();
        ZipnovaWebhookService service = new ZipnovaWebhookService(properties, inbox, shipments, gateway,
                Clock.systemUTC(), completion);
        var instruction = new ZipnovaWebhookService.Instruction(UUID.randomUUID(), UUID.randomUUID(), 99L);
        var provider = new ZipnovaGateway.ProviderShipment(99L, "PIN-42", "canceled", null, null, null, null,
                Instant.now(), "Andreani");
        when(gateway.getShipment(99L)).thenReturn(provider);
        when(gateway.tracking(99L)).thenThrow(new com.computerstore.shipping.gateway.ShippingProviderException(
                "tracking unavailable", null, false, true, null));

        service.process(instruction);

        verify(shipments).applyWebhook(99L, provider, List.of());
        verify(completion).complete(instruction, true, null);
    }
    private ZipnovaWebhookService service(ShippingWebhookInboxRepository inbox) {
        return new ZipnovaWebhookService(properties(), inbox, mock(ShipmentDispatchService.class),
                mock(ZipnovaGateway.class), Clock.systemUTC(), mock(ZipnovaWebhookCompletionService.class));
    }
    private ZipnovaProperties properties() { return new ZipnovaProperties(true, true, "token", "secret", 7L, 12L,
            "pinatech", "dynamic", Duration.ofMinutes(15), Duration.ofSeconds(1), Duration.ofSeconds(2),
            "012345678901234567890123", Duration.ofMinutes(10)); }
}
