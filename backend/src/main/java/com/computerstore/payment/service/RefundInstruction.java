package com.computerstore.payment.service;

import java.util.UUID;

record RefundInstruction(UUID attemptId, String paymentId, UUID idempotencyKey, Long eventId) {
}
