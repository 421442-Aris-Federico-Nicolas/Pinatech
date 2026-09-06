package com.computerstore.shipping.service;

import java.util.List;

import com.computerstore.shipping.config.ZipnovaProperties;
import com.computerstore.shipping.gateway.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ShipmentWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShipmentWorker.class);
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
    @Scheduled(fixedDelay = 10000)
    public void cancelDue() {
        if (!properties.available()) return;
        for (int processed = 0; processed < BATCH_SIZE; processed++) {
            var instruction = transactions.claimCancellation();
            if (instruction.isEmpty()) return;
            cancel(instruction.get());
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
            List<ZipnovaGateway.TrackingEvent> history = List.of();
            boolean trackingComplete = true;
            try {
                history = gateway.tracking(instruction.providerId());
            } catch (ShippingProviderException error) {
                trackingComplete = false;
                LOGGER.warn("Zipnova tracking lookup failed for shipment {}; applying provider state without history.",
                        instruction.providerId());
            }
            transactions.reconciled(instruction.id(), instruction.token(), shipment, history, trackingComplete);
        } catch (ShippingProviderException error) { transactions.reconciliationFailed(instruction.id(), instruction.token()); }
    }
    private void cancel(ShipmentDispatchService.CancellationRetryInstruction instruction) {
        try {
            if (!instruction.providerCancellationRequired()) {
                transactions.cancellationSucceeded(instruction.id(), instruction.token(),
                        instruction.orderId(), instruction.providerId());
                return;
            }
            gateway.cancel(instruction.providerId());
            transactions.cancellationSucceeded(instruction.id(), instruction.token(),
                    instruction.orderId(), instruction.providerId());
        } catch (ShippingProviderException error) {
            if (error.retryable()) {
                transactions.cancellationFailed(instruction.id(), instruction.token(), error.getMessage());
                return;
            }
            resolveRejectedCancellation(instruction, error);
        }
    }
    private void resolveRejectedCancellation(ShipmentDispatchService.CancellationRetryInstruction instruction,
                                             ShippingProviderException cancellationError) {
        try {
            var provider = gateway.getShipment(instruction.providerId());
            if ("cancelled".equalsIgnoreCase(provider.status()) || "canceled".equalsIgnoreCase(provider.status())) {
                transactions.cancellationSucceeded(instruction.id(), instruction.token(),
                        instruction.orderId(), instruction.providerId());
            } else {
                transactions.cancellationRejected(instruction.id(), instruction.token(), cancellationError.getMessage());
            }
        } catch (ShippingProviderException lookupError) {
            transactions.cancellationFailed(instruction.id(), instruction.token(),
                    "Cancellation result could not be confirmed: " + lookupError.getMessage());
        }
    }
}
