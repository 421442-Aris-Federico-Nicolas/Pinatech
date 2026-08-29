package com.computerstore.payment.dto;

import com.computerstore.payment.domain.BankTransferProof;

import java.time.Instant;
import java.util.UUID;

public record BankTransferProofResponse(UUID id, String status, String originalFilename, String contentType,
                                        long sizeBytes, Instant submittedAt, Instant reviewedAt,
                                        String rejectionReason, int previewCount) {
    public static BankTransferProofResponse from(BankTransferProof proof) {
        return new BankTransferProofResponse(proof.getId(), proof.getStatus().name(), proof.getOriginalFilename(),
                proof.getContentType(), proof.getSizeBytes(), proof.getSubmittedAt(), proof.getReviewedAt(),
                proof.getRejectionReason(), proof.getPreviews().size());
    }
}
