package com.computerstore.email;

import com.computerstore.user.domain.AccountActionPurpose;
import java.util.UUID;
import java.time.Duration;

public interface TransactionalEmailService {

    void sendEmailVerification(String recipient, String firstName, String rawToken);

    void sendAccountAction(String recipient, String firstName, AccountActionPurpose purpose, String rawToken);

    void sendEmailChangedNotice(String previousEmail, String firstName, String newEmail);

    void sendGuestCheckoutCode(String recipient, String firstName, String code, Duration ttl);

    void sendGuestOrderCreated(String recipient, String firstName, UUID publicId, String rawAccessToken);

    void sendOrderEvent(UUID idempotencyKey, String recipient, String customerName,
                        OrderEmailEventType eventType, Long orderId, UUID publicId, boolean guestOrder,
                        String rejectionReason);

    void sendSellerOrderEvent(UUID idempotencyKey, String recipient, OrderEmailEventType eventType,
                              SellerOrderSnapshot snapshot);

    void sendShipmentTracking(UUID idempotencyKey, String recipient, String customerName, Long orderId,
                              ShipmentTrackingSnapshot snapshot);
}
