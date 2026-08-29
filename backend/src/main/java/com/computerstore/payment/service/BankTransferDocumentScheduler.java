package com.computerstore.payment.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BankTransferDocumentScheduler {
    private final BankTransferDocumentMaintenance maintenance;
    public BankTransferDocumentScheduler(BankTransferDocumentMaintenance maintenance) {
        this.maintenance = maintenance;
    }

    @Scheduled(fixedDelayString = "${app.payments.bank-transfer.retention-interval-ms:3600000}")
    public void retention() { maintenance.applyRetention(); }

    @Scheduled(fixedDelayString = "${app.payments.bank-transfer.orphan-interval-ms:86400000}")
    public void orphans() { maintenance.reconcileOrphans(); }
}
