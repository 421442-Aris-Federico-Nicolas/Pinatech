package com.computerstore.email;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailOutboxCompletionService {
    private final EmailOutboxRepository entries;
    private final Clock clock;

    public EmailOutboxCompletionService(EmailOutboxRepository entries, Clock clock) {
        this.entries = entries;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void success(UUID id, UUID leaseToken) {
        entries.findByIdForUpdate(id).filter(entry -> entry.hasLease(leaseToken)).ifPresent(entry -> {
            entry.sent(Instant.now(clock));
            entries.save(entry);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failure(UUID id, UUID leaseToken, RuntimeException error) {
        entries.findByIdForUpdate(id).filter(entry -> entry.hasLease(leaseToken)).ifPresent(entry -> {
            String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            entry.failed(Instant.now(clock), message);
            entries.save(entry);
        });
    }
}
