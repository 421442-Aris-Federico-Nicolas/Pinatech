package com.computerstore.shipping.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import java.time.*;
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
    private ZipnovaWebhookService service(ShippingWebhookInboxRepository inbox) {
        var properties = new ZipnovaProperties(true, true, "token", "secret", 7L, 12L, "pinatech", "dynamic",
                Duration.ofMinutes(15), Duration.ofSeconds(1), Duration.ofSeconds(2),
                "012345678901234567890123", Duration.ofMinutes(10));
        return new ZipnovaWebhookService(properties, inbox, mock(ShipmentDispatchService.class),
                mock(ZipnovaGateway.class), Clock.systemUTC(), mock(ZipnovaWebhookCompletionService.class));
    }
}
