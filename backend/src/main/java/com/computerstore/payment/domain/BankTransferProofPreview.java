package com.computerstore.payment.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "bank_transfer_proof_previews")
public class BankTransferProofPreview {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proof_id", nullable = false)
    private BankTransferProof proof;
    @Column(name = "storage_key", nullable = false, unique = true)
    private String storageKey;
    @Column(name = "preview_index", nullable = false)
    private int previewIndex;
    @Column(nullable = false)
    private int width;
    @Column(nullable = false)
    private int height;
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;
    @Column(nullable = false, length = 64)
    private String sha256;

    protected BankTransferProofPreview() {}
    BankTransferProofPreview(BankTransferProof proof, String storageKey, int previewIndex,
                             int width, int height, long sizeBytes, String sha256) {
        this.proof = proof; this.storageKey = storageKey; this.previewIndex = previewIndex;
        this.width = width; this.height = height; this.sizeBytes = sizeBytes; this.sha256 = sha256;
    }
    public String getStorageKey() { return storageKey; }
    public int getPreviewIndex() { return previewIndex; }
}
