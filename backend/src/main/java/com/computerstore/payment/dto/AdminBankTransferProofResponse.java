package com.computerstore.payment.dto;

import com.computerstore.payment.domain.BankTransferProof;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AdminBankTransferProofResponse(
        UUID id,
        String status,
        String originalFilename,
        String contentType,
        long sizeBytes,
        Instant submittedAt,
        Instant reviewedAt,
        String rejectionReason,
        int previewCount,
        Long orderId,
        String customerName,
        String customerEmail,
        BigDecimal total,
        String currency
) {
    public static AdminBankTransferProofResponse from(BankTransferProof proof) {
        var order = proof.getOrder();
        var user = order.getUser();
        return new AdminBankTransferProofResponse(proof.getId(), proof.getStatus().name(),
                proof.getOriginalFilename(), proof.getContentType(), proof.getSizeBytes(), proof.getSubmittedAt(),
                proof.getReviewedAt(), proof.getRejectionReason(), proof.getPreviews().size(), order.getId(),
                user.getFirstName() + " " + user.getLastName(), user.getEmail(), order.getTotal(),
                order.getCurrency());
    }
}
