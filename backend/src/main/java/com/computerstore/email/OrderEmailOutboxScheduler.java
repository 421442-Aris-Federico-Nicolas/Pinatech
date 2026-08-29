package com.computerstore.email;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrderEmailOutboxScheduler {
    private final OrderEmailOutboxService outbox;

    public OrderEmailOutboxScheduler(OrderEmailOutboxService outbox) { this.outbox = outbox; }

    @Scheduled(fixedDelayString = "${app.email.outbox-interval-ms:10000}")
    public void deliver() {
        for (int count = 0; count < 25; count++) {
            var instruction = outbox.claim();
            if (instruction.isEmpty()) return;
            outbox.deliver(instruction.get());
        }
    }
}
