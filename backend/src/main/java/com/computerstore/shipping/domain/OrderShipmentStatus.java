package com.computerstore.shipping.domain;

public enum OrderShipmentStatus {
    PENDING_CREATE, CREATING, ACTIVE, RETRY, BLOCKED_PAYMENT, CANCELLED, DELIVERED, INCIDENT, FAILED
}
