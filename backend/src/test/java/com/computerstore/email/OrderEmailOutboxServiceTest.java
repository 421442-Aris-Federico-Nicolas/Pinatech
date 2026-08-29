package com.computerstore.email;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class OrderEmailOutboxServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-29T10:00:00Z");

    @Test
    void classifiesAnExceptionWithoutAMessageAsFailure() {
        EmailOutboxRepository entries = mock(EmailOutboxRepository.class);
        TransactionalEmailService email = mock(TransactionalEmailService.class);
        EmailOutboxCompletionService completion = mock(EmailOutboxCompletionService.class);
        OrderEmailOutboxService service = new OrderEmailOutboxService(
                entries, email, completion, Clock.fixed(NOW, ZoneOffset.UTC));
        UUID id = UUID.randomUUID();
        UUID leaseToken = UUID.randomUUID();
        var instruction = new OrderEmailOutboxService.Instruction(
                id, leaseToken, OrderEmailEventType.ORDER_CREATED, 41L,
                "customer@example.com", "Ada", null);
        RuntimeException providerFailure = new RuntimeException();
        org.mockito.Mockito.doThrow(providerFailure).when(email).sendOrderEvent(
                id, "customer@example.com", "Ada", OrderEmailEventType.ORDER_CREATED, 41L, null);

        service.deliver(instruction);

        verify(completion).failure(id, leaseToken, providerFailure);
    }

    @Test
    void completionIgnoresAStaleLeaseToken() {
        EmailOutboxRepository entries = mock(EmailOutboxRepository.class);
        EmailOutboxEntry entry = mock(EmailOutboxEntry.class);
        UUID id = UUID.randomUUID();
        UUID staleToken = UUID.randomUUID();
        when(entries.findByIdForUpdate(id)).thenReturn(Optional.of(entry));
        when(entry.hasLease(staleToken)).thenReturn(false);
        EmailOutboxCompletionService completion = new EmailOutboxCompletionService(
                entries, Clock.fixed(NOW, ZoneOffset.UTC));

        completion.success(id, staleToken);

        verify(entry, org.mockito.Mockito.never()).sent(NOW);
    }
}
