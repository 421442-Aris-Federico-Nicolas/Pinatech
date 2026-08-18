package com.computerstore.payment.domain;

public enum PaymentAttemptStatus {
    CREATED,
    PREFERENCE_CREATED,
    PENDING,
    REJECTED,
    APPROVED,
    REFUND_PENDING,
    REFUNDED
}
