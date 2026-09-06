package com.computerstore.payment.service;

import java.util.UUID;

public record RefundInstruction(UUID attemptId, String paymentId, UUID idempotencyKey, String refundId, Long eventId) {
}
