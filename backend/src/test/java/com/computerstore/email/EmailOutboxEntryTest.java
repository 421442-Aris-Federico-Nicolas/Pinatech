package com.computerstore.email;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;

import com.computerstore.order.domain.CustomerOrder;
import com.computerstore.user.domain.UserAccount;
import org.junit.jupiter.api.Test;

class EmailOutboxEntryTest {

    @Test
    void movesPermanentlyFailedMessagesToTheTerminalQueue() {
        CustomerOrder order = mock(CustomerOrder.class);
        UserAccount user = mock(UserAccount.class);
        when(order.getUser()).thenReturn(user);
        when(user.getEmail()).thenReturn("customer@example.com");
        when(user.getFirstName()).thenReturn("Ada");
        EmailOutboxEntry entry = new EmailOutboxEntry(
                order, OrderEmailEventType.ORDER_CREATED, null, Instant.parse("2026-08-29T10:00:00Z"));

        for (int attempt = 1; attempt < EmailOutboxEntry.MAX_ATTEMPTS; attempt++) {
            entry.failed(Instant.parse("2026-08-29T10:00:00Z"), "provider unavailable");
            assertEquals(EmailOutboxStatus.PENDING, entry.getStatus());
        }
        entry.failed(Instant.parse("2026-08-29T10:00:00Z"), "provider unavailable");

        assertEquals(EmailOutboxEntry.MAX_ATTEMPTS, entry.getAttemptCount());
        assertEquals(EmailOutboxStatus.FAILED, entry.getStatus());
    }
}
