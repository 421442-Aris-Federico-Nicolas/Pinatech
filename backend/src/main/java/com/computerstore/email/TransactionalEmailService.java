package com.computerstore.email;

import com.computerstore.user.domain.AccountActionPurpose;
import java.util.UUID;

public interface TransactionalEmailService {

    void sendEmailVerification(String recipient, String firstName, String rawToken);

    void sendAccountAction(String recipient, String firstName, AccountActionPurpose purpose, String rawToken);

    void sendEmailChangedNotice(String previousEmail, String firstName, String newEmail);

    void sendOrderEvent(UUID idempotencyKey, String recipient, String customerName,
                        OrderEmailEventType eventType, Long orderId, String rejectionReason);

    void sendSellerOrderEvent(UUID idempotencyKey, String recipient, OrderEmailEventType eventType,
                              SellerOrderSnapshot snapshot);
}
