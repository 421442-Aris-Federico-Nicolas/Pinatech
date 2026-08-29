package com.computerstore.email;

import com.computerstore.order.domain.CustomerOrder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderEmailOutboxService {
    private final EmailOutboxRepository entries;
    private final TransactionalEmailService email;
    private final EmailOutboxCompletionService completion;
    private final Clock clock;

    public OrderEmailOutboxService(EmailOutboxRepository entries, TransactionalEmailService email,
                                   EmailOutboxCompletionService completion, Clock clock) {
        this.entries = entries;
        this.email = email;
        this.completion = completion;
        this.clock = clock;
    }

    public void enqueue(CustomerOrder order, OrderEmailEventType event) { enqueue(order, event, null); }

    public void enqueue(CustomerOrder order, OrderEmailEventType event, String reason) {
        entries.save(new EmailOutboxEntry(order, event, reason, Instant.now(clock)));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Instruction> claim() {
        Instant now = Instant.now(clock);
        return entries.findNextDueForUpdate(now).map(entry -> {
            UUID leaseToken = entry.lease(now.plusSeconds(60));
            return new Instruction(entry.getId(), leaseToken, entry.getEventType(), entry.getOrder().getId(),
                    entry.getRecipient(), entry.getCustomerName(), entry.getRejectionReason());
        });
    }

    public void deliver(Instruction instruction) {
        try {
            email.sendOrderEvent(instruction.id(), instruction.recipient(), instruction.customerName(),
                    instruction.eventType(), instruction.orderId(), instruction.rejectionReason());
            completion.success(instruction.id(), instruction.leaseToken());
        } catch (RuntimeException exception) {
            completion.failure(instruction.id(), instruction.leaseToken(), exception);
        }
    }

    public record Instruction(UUID id, UUID leaseToken, OrderEmailEventType eventType, Long orderId, String recipient,
                               String customerName, String rejectionReason) {}
}
