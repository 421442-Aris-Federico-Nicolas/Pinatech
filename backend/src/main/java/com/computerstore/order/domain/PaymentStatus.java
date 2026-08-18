package com.computerstore.order.domain;

public enum PaymentStatus {
    PENDING,
    APPROVED,
    REJECTED,
    EXPIRED,
    CANCELLED,
    REFUND_PENDING,
    REFUNDED,
    IN_MEDIATION,
    CHARGEBACK
}
