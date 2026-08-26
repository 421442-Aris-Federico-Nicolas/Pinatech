package com.computerstore.email;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.computerstore.user.domain.AccountActionPurpose;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ResendTransactionalEmailServiceTest {

    @Test
    void enabledEmailRequiresCompleteProviderConfiguration() {
        assertThrows(IllegalStateException.class, () ->
                new ResendTransactionalEmailService(true, "", "Pinatech <accounts@example.com>",
                        "https://store.example.com", new ObjectMapper()));
        assertThrows(IllegalStateException.class, () ->
                new ResendTransactionalEmailService(true, "key", "Pinatech <accounts@example.com>",
                        "not-a-url", new ObjectMapper()));
    }

    @Test
    void disabledEmailDoesNotAttemptExternalDelivery() {
        ResendTransactionalEmailService service = new ResendTransactionalEmailService(
                false, "", "", "https://store.example.com", new ObjectMapper());

        assertDoesNotThrow(() -> service.sendAccountAction(
                "user@example.com", AccountActionPurpose.EMAIL_VERIFICATION, "raw-token"));
        assertDoesNotThrow(() -> service.sendEmailChangedNotice(
                "old@example.com", "new@example.com"));
    }
}
