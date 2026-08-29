package com.computerstore.payment.service;

import com.computerstore.common.exception.*;
import com.computerstore.email.OrderEmailEventType;
import com.computerstore.email.OrderEmailOutboxService;
import com.computerstore.order.domain.OrderStatus;
import com.computerstore.order.domain.PaymentMethod;
import com.computerstore.order.repository.CustomerOrderRepository;
import com.computerstore.order.service.OrderStockService;
import com.computerstore.payment.domain.BankTransferProof;
import com.computerstore.payment.domain.BankTransferProofStatus;
import com.computerstore.payment.dto.*;
import com.computerstore.payment.repository.BankTransferProofRepository;
import com.computerstore.security.AuthenticatedUser;
import com.computerstore.storage.PrivateDocumentStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

@Service
public class BankTransferService {
    private final CustomerOrderRepository orders;
    private final BankTransferProofRepository proofs;
    private final OrderStockService stock;
    private final PrivateDocumentStorage storage;
    private final OrderEmailOutboxService outbox;
    private final Clock clock;

    public BankTransferService(CustomerOrderRepository orders, BankTransferProofRepository proofs,
            OrderStockService stock, PrivateDocumentStorage storage,
            OrderEmailOutboxService outbox, Clock clock) {
        this.orders = orders; this.proofs = proofs; this.stock = stock; this.storage = storage;
        this.outbox = outbox; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public BankTransferDetailResponse detail(Long orderId, AuthenticatedUser auth) {
        var order = transferOrder(orderId);
        authorizeOwnerOrAdmin(order.getUser().getId(), auth);
        return detail(order, proofs.findByOrderId(orderId).orElse(null));
    }

    @Transactional(noRollbackFor = ReservationExpiredException.class)
    public BankTransferDetailResponse upload(Long orderId, BankTransferProofSanitizer.SanitizedProof sanitized,
                                             String suppliedKey,
                                              AuthenticatedUser auth) {
        var order = orders.findByIdForUpdate(orderId)
                .filter(candidate -> candidate.getPaymentMethod() == PaymentMethod.BANK_TRANSFER)
                .orElseThrow(() -> new ResourceNotFoundException("Bank transfer order not found."));
        authorizeOwnerOrAdmin(order.getUser().getId(), auth);
        String key = normalizeIdempotencyKey(suppliedKey);
        Optional<BankTransferProof> existing = proofs.findByOrderId(orderId);
        if (existing.isPresent()) {
            if (key != null && key.equals(existing.get().getIdempotencyKey())) return detail(order, existing.get());
            throw new DuplicateResourceException("This order already has a transfer proof.");
        }
        Instant now = Instant.now(clock);
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new InvalidStateTransitionException("This order no longer accepts a transfer proof.");
        }
        if (!order.getPaymentDueAt().isAfter(now)) {
            stock.release(order);
            order.expire();
            throw new ReservationExpiredException("The bank transfer proof deadline has expired.");
        }

        List<String> storedKeys = new ArrayList<>();
        cleanupOnRollback(storedKeys);
        try {
            var raw = storage.store(sanitized.raw());
            storedKeys.add(raw.storageKey());
            BankTransferProof proof = new BankTransferProof(order, raw.storageKey(), sanitized.originalFilename(),
                    sanitized.contentType(), sanitized.uploadedSize(), raw.sha256(), key, now);
            int index = 0;
            for (var preview : sanitized.previews()) {
                var stored = index == 0 && Arrays.equals(sanitized.raw(), preview.content())
                        ? raw
                        : storage.store(preview.content());
                if (stored != raw) storedKeys.add(stored.storageKey());
                proof.addPreview(stored.storageKey(), index++, preview.width(), preview.height(),
                        stored.sizeBytes(), stored.sha256());
            }
            proofs.saveAndFlush(proof);
            order.submitBankTransferProof();
            return detail(order, proof);
        } catch (RuntimeException exception) {
            deleteAll(storedKeys, exception);
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public BankTransferDetailResponse preflightUpload(Long orderId, String suppliedKey, AuthenticatedUser auth) {
        var order = transferOrder(orderId);
        authorizeOwnerOrAdmin(order.getUser().getId(), auth);
        String key = normalizeIdempotencyKey(suppliedKey);
        Optional<BankTransferProof> existing = proofs.findByOrderId(orderId);
        if (existing.isEmpty()) return null;
        if (key != null && key.equals(existing.get().getIdempotencyKey())) return detail(order, existing.get());
        throw new DuplicateResourceException("This order already has a transfer proof.");
    }

    @Transactional(readOnly = true)
    public List<AdminBankTransferProofResponse> list(BankTransferProofStatus status) {
        return proofs.findByStatusOrderBySubmittedAtAsc(status).stream()
                .map(AdminBankTransferProofResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PreviewContent preview(UUID proofId, int index) {
        BankTransferProof proof = proofs.findById(proofId)
                .orElseThrow(() -> new ResourceNotFoundException("Bank transfer proof not found."));
        if (index < 0 || index >= proof.getPreviews().size()) {
            throw new ResourceNotFoundException("Proof preview not found.");
        }
        Path path = storage.load(proof.getPreviews().get(index).getStorageKey());
        return new PreviewContent(path);
    }

    @Transactional
    public BankTransferProofResponse approve(UUID proofId, BigDecimal amount, String suppliedReference,
                                              AuthenticatedUser reviewer) {
        Long reviewerId = requireAdmin(reviewer);
        BankTransferProof proof = pendingProof(proofId);
        var order = orders.findByIdForUpdate(proof.getOrder().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found."));
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new InvalidStateTransitionException("A cancelled or completed transfer order cannot be approved.");
        }
        if (amount == null || amount.compareTo(order.getTotal()) != 0) {
            throw new InvalidRequestException("The approved amount must exactly match the order total.");
        }
        String reference = normalizeReference(suppliedReference);
        if (proofs.existsByBankReference(reference)) {
            throw new DuplicateResourceException("This bank reference was already used.");
        }
        Instant now = Instant.now(clock);
        order.approveBankTransfer();
        proof.approve(now, reference, amount, reviewerId);
        try {
            proofs.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateResourceException("This bank reference was already used.");
        }
        outbox.enqueue(order, OrderEmailEventType.PAYMENT_APPROVED);
        return BankTransferProofResponse.from(proof);
    }

    @Transactional
    public BankTransferProofResponse reject(UUID proofId, String suppliedReason, AuthenticatedUser reviewer) {
        Long reviewerId = requireAdmin(reviewer);
        String reason = suppliedReason == null ? "" : suppliedReason.trim();
        if (reason.isEmpty() || reason.length() > 1000) {
            throw new InvalidRequestException("A rejection reason is required and must not exceed 1000 characters.");
        }
        BankTransferProof proof = pendingProof(proofId);
        var order = orders.findByIdForUpdate(proof.getOrder().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found."));
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new InvalidStateTransitionException("A cancelled or completed transfer order cannot be rejected.");
        }
        stock.release(order);
        order.rejectBankTransfer();
        proof.reject(Instant.now(clock), reason, reviewerId);
        outbox.enqueue(order, OrderEmailEventType.BANK_TRANSFER_REJECTED, reason);
        return BankTransferProofResponse.from(proof);
    }

    private BankTransferProof pendingProof(UUID id) {
        return proofs.findByIdForUpdate(id)
                .filter(proof -> proof.getStatus() == BankTransferProofStatus.PENDING_REVIEW)
                .orElseThrow(() -> new InvalidStateTransitionException("The transfer proof is not awaiting review."));
    }

    private com.computerstore.order.domain.CustomerOrder transferOrder(Long id) {
        return orders.findById(id).filter(order -> order.getPaymentMethod() == PaymentMethod.BANK_TRANSFER)
                .orElseThrow(() -> new ResourceNotFoundException("Bank transfer order not found."));
    }

    private BankTransferDetailResponse detail(com.computerstore.order.domain.CustomerOrder order,
                                               BankTransferProof proof) {
        return new BankTransferDetailResponse(order.getId(), order.getPaymentDueAt(),
                BankAccountResponse.from(order.getBankAccount()),
                proof == null ? null : BankTransferProofResponse.from(proof));
    }

    private void authorizeOwnerOrAdmin(Long ownerId, AuthenticatedUser auth) {
        boolean admin = auth != null && auth.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
        if (!admin && (auth == null || !ownerId.equals(auth.id()))) {
            throw new ResourceNotFoundException("Bank transfer order not found.");
        }
    }

    private String normalizeIdempotencyKey(String supplied) {
        if (supplied == null) return null;
        String value = supplied.trim();
        if (value.isEmpty() || value.length() > 100) {
            throw new InvalidRequestException("Idempotency-Key must contain between 1 and 100 characters.");
        }
        return value;
    }

    private String normalizeReference(String supplied) {
        String value = supplied == null ? "" : supplied.toUpperCase(Locale.ROOT).replaceAll("[\\s-]", "");
        if (!value.matches("[A-Z0-9]{6,100}")) {
            throw new InvalidRequestException("Bank reference must contain 6 to 100 letters or digits.");
        }
        return value;
    }

    private Long requireAdmin(AuthenticatedUser reviewer) {
        if (reviewer == null || reviewer.getAuthorities().stream()
                .noneMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"))) {
            throw new org.springframework.security.access.AccessDeniedException("Administrator access is required.");
        }
        return reviewer.id();
    }

    private void cleanupOnRollback(List<String> keys) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) deleteAll(keys, null);
            }
        });
    }

    private void deleteAll(List<String> keys, RuntimeException original) {
        for (String key : keys) try { storage.delete(key); } catch (RuntimeException cleanup) {
            if (original != null) original.addSuppressed(cleanup);
        }
    }

    public record PreviewContent(Path path) {}
}
