package com.computerstore.payment.domain;

import com.computerstore.order.domain.CustomerOrder;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.time.ZoneOffset;
import java.math.BigDecimal;

@Entity
@Table(name = "bank_transfer_proofs")
public class BankTransferProof {
    @Id
    private UUID id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private CustomerOrder order;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BankTransferProofStatus status;
    @Column(name = "storage_key")
    private String storageKey;
    @Column(name = "original_filename", length = 255)
    private String originalFilename;
    @Column(name = "content_type", length = 100)
    private String contentType;
    @Column(name = "size_bytes")
    private Long sizeBytes;
    @Column(nullable = false, length = 64)
    private String sha256;
    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;
    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;
    @Column(name = "reviewed_at")
    private Instant reviewedAt;
    @Column(name = "approved_at")
    private Instant approvedAt;
    @Column(name = "approved_amount", precision = 19, scale = 2)
    private BigDecimal approvedAmount;
    @Column(name = "reviewed_by_user_id")
    private Long reviewedByUserId;
    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;
    @Column(name = "bank_reference", length = 100)
    private String bankReference;
    @Column(name = "retain_until")
    private Instant retainUntil;
    @Column(name = "file_deleted_at")
    private Instant fileDeletedAt;
    @OneToMany(mappedBy = "proof", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("previewIndex ASC")
    private List<BankTransferProofPreview> previews = new ArrayList<>();

    protected BankTransferProof() {}

    public BankTransferProof(CustomerOrder order, String storageKey, String filename, String contentType,
                             long sizeBytes, String sha256, String idempotencyKey, Instant submittedAt) {
        this.id = UUID.randomUUID();
        this.order = order;
        this.status = BankTransferProofStatus.PENDING_REVIEW;
        this.storageKey = storageKey;
        this.originalFilename = filename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.idempotencyKey = idempotencyKey;
        this.submittedAt = submittedAt;
    }

    public void addPreview(String key, int index, int width, int height, long size, String hash) {
        previews.add(new BankTransferProofPreview(this, key, index, width, height, size, hash));
    }

    public void approve(Instant now, String reference, BigDecimal amount, Long reviewerUserId) {
        status = BankTransferProofStatus.APPROVED;
        reviewedAt = now;
        approvedAt = now;
        bankReference = reference;
        approvedAmount = amount;
        reviewedByUserId = reviewerUserId;
        retainUntil = now.atZone(ZoneOffset.UTC).plusYears(5).toInstant();
    }

    public void reject(Instant now, String reason, Long reviewerUserId) {
        status = BankTransferProofStatus.REJECTED;
        reviewedAt = now;
        rejectionReason = reason;
        reviewedByUserId = reviewerUserId;
        retainUntil = now.plusSeconds(90L * 24 * 60 * 60);
    }

    public void fileDeleted(Instant now) {
        status = BankTransferProofStatus.FILE_DELETED;
        storageKey = null;
        originalFilename = null;
        contentType = null;
        sizeBytes = null;
        fileDeletedAt = now;
        previews.clear();
    }

    public UUID getId() { return id; }
    public CustomerOrder getOrder() { return order; }
    public BankTransferProofStatus getStatus() { return status; }
    public String getStorageKey() { return storageKey; }
    public String getOriginalFilename() { return originalFilename; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes == null ? 0 : sizeBytes; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getRejectionReason() { return rejectionReason; }
    public Instant getRetainUntil() { return retainUntil; }
    public List<BankTransferProofPreview> getPreviews() { return List.copyOf(previews); }
}
