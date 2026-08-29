package com.computerstore.payment.service;

import com.computerstore.payment.repository.BankTransferProofRepository;
import com.computerstore.storage.PrivateDocumentStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.ArrayList;

@Service
public class BankTransferDocumentMaintenance {
    private final BankTransferProofRepository proofs;
    private final PrivateDocumentStorage storage;
    private final Clock clock;

    public BankTransferDocumentMaintenance(BankTransferProofRepository proofs,
            PrivateDocumentStorage storage, Clock clock) {
        this.proofs = proofs; this.storage = storage; this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int applyRetention() {
        Instant now = Instant.now(clock);
        int deleted = 0;
        for (var proof : proofs.findDueForDeletion(now)) {
            var storageKeys = new ArrayList<String>();
            storageKeys.add(proof.getStorageKey());
            for (var preview : proof.getPreviews()) storageKeys.add(preview.getStorageKey());
            proof.fileDeleted(now);
            deleteAfterCommit(storageKeys);
            deleted++;
        }
        return deleted;
    }

    @Transactional(readOnly = true)
    public int reconcileOrphans() {
        var referenced = new HashSet<>(proofs.findAllRawStorageKeys());
        referenced.addAll(proofs.findAllPreviewStorageKeys());
        int deleted = 0;
        for (var file : storage.filesOlderThan(Instant.now(clock).minus(24, ChronoUnit.HOURS))) {
            if (!referenced.contains(file.storageKey())) {
                storage.delete(file.storageKey());
                deleted++;
            }
        }
        return deleted;
    }

    private void deleteAfterCommit(Iterable<String> storageKeys) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (String storageKey : storageKeys) {
                    try {
                        storage.delete(storageKey);
                    } catch (RuntimeException ignored) {
                        // The orphan reconciler retries files left behind after a committed retention update.
                    }
                }
            }
        });
    }
}
