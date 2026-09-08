package com.computerstore.email;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;

import com.computerstore.order.domain.CustomerOrder;
import com.computerstore.order.domain.BuyerSnapshot;
import org.junit.jupiter.api.Test;

class EmailOutboxEntryTest {

    @Test
    void movesPermanentlyFailedMessagesToTheTerminalQueue() {
        CustomerOrder order = mock(CustomerOrder.class);
        when(order.getBuyer()).thenReturn(new BuyerSnapshot(
                "Ada", "Lovelace", "customer@example.com", "3515550101", "12345678"));
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
