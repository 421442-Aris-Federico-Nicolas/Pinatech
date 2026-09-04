package com.computerstore.shipping.service;

import com.computerstore.shipping.config.ZipnovaProperties;
import com.computerstore.shipping.gateway.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ShipmentWorker {
    private static final int BATCH_SIZE = 25;
    private final ShipmentDispatchService transactions; private final ZipnovaGateway gateway;
    private final ZipnovaProperties properties;
    public ShipmentWorker(ShipmentDispatchService transactions, ZipnovaGateway gateway, ZipnovaProperties properties) {
        this.transactions = transactions; this.gateway = gateway; this.properties = properties;
    }
    @Scheduled(fixedDelay = 10000)
    public void createDue() {
        if (!properties.available()) return;
        for (int processed = 0; processed < BATCH_SIZE; processed++) {
            var instruction = transactions.claimCreation();
            if (instruction.isEmpty()) return;
            create(instruction.get());
        }
    }
    @Scheduled(fixedDelay = 5000)
    public void reconcileDue() {
        if (!properties.available()) return;
        for (int processed = 0; processed < BATCH_SIZE; processed++) {
            var instruction = transactions.claimReconciliation();
            if (instruction.isEmpty()) return;
            reconcile(instruction.get());
        }
    }
    private void create(ShipmentDispatchService.CreateInstruction instruction) {
        try {
            var existing = gateway.findByExternalId(instruction.externalId());
            var shipment = existing == null ? gateway.createShipment(instruction.command()) : existing;
            transactions.creationSucceeded(instruction.id(), instruction.token(), shipment);
        } catch (ShippingProviderException error) { transactions.creationFailed(instruction.id(), instruction.token(), error); }
    }
    private void reconcile(ShipmentDispatchService.ReconcileInstruction instruction) {
        try {
            var shipment = gateway.getShipment(instruction.providerId());
            transactions.reconciled(instruction.id(), instruction.token(), shipment, gateway.tracking(instruction.providerId()));
        } catch (ShippingProviderException error) { transactions.reconciliationFailed(instruction.id(), instruction.token()); }
    }
}
