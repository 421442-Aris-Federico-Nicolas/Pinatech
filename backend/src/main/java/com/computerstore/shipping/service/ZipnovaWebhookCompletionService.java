package com.computerstore.shipping.service;

import java.time.Clock;
import java.time.Instant;

import com.computerstore.shipping.repository.ShippingWebhookInboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ZipnovaWebhookCompletionService {
    private final ShippingWebhookInboxRepository inbox;
    private final Clock clock;

    public ZipnovaWebhookCompletionService(ShippingWebhookInboxRepository inbox, Clock clock) {
        this.inbox = inbox;
        this.clock = clock;
    }

    @Transactional
    public void complete(ZipnovaWebhookService.Instruction instruction, boolean success, String error) {
        inbox.findByIdForUpdate(instruction.id()).ifPresent(item -> {
            if (success) item.done(instruction.token(), Instant.now(clock));
            else item.failed(instruction.token(), Instant.now(clock), error);
        });
    }
}
