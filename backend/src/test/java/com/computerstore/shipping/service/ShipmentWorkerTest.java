package com.computerstore.shipping.service;

import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import com.computerstore.shipping.config.ZipnovaProperties;
import com.computerstore.shipping.gateway.ZipnovaGateway;
import org.junit.jupiter.api.Test;

class ShipmentWorkerTest {
    @Test
    void ambiguousRetryFindsExternalIdAndDoesNotCreateDuplicateShipment() {
        ShipmentDispatchService transactions = mock(ShipmentDispatchService.class);
        ZipnovaGateway gateway = mock(ZipnovaGateway.class);
        var command = new ZipnovaGateway.CreateShipmentCommand("PIN-42",
                new ZipnovaGateway.Destination("Ada", "12345678", "a@b.com", "3515550000", "A", "1", null, "C", "Córdoba", "5000"),
                BigDecimal.TEN, "standard", "carrier_pickup", 3, List.of(new ZipnovaGateway.Item(10,1,1,1,"1","x",false)));
        UUID id = UUID.randomUUID(), token = UUID.randomUUID();
        when(transactions.claimCreation()).thenReturn(
                Optional.of(new ShipmentDispatchService.CreateInstruction(id, token, "PIN-42", command)),
                Optional.empty());
        var existing = new ZipnovaGateway.ProviderShipment(99, "PIN-42", "new", null, null, null, null, Instant.now(), "Andreani");
        when(gateway.findByExternalId("PIN-42")).thenReturn(existing);
        ShipmentWorker worker = new ShipmentWorker(transactions, gateway, properties());
        worker.createDue();
        verify(gateway, never()).createShipment(any()); verify(transactions).creationSucceeded(id, token, existing);
    }
    private ZipnovaProperties properties() { return new ZipnovaProperties(true, true, "token", "secret", 7L, 12L,
            "pinatech", "dynamic", Duration.ofMinutes(15), Duration.ofSeconds(1), Duration.ofSeconds(2),
            "012345678901234567890123", Duration.ofMinutes(10)); }
}
