package com.computerstore.shipping.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import com.computerstore.shipping.repository.ShippingQuoteRepository;
import com.computerstore.shipping.repository.ShippingWebhookInboxRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ShippingRetentionWorker {
    private final ShippingQuoteRepository quotes;
    private final ShippingWebhookInboxRepository webhooks;
    private final Clock clock;

    public ShippingRetentionWorker(ShippingQuoteRepository quotes, ShippingWebhookInboxRepository webhooks, Clock clock) {
        this.quotes = quotes;
        this.webhooks = webhooks;
        this.clock = clock;
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void cleanExpiredRecords() {
        Instant now = Instant.now(clock);
        quotes.deleteExpiredUnconsumed(now.minus(Duration.ofDays(1)));
        webhooks.deleteProcessedBefore(now.minus(Duration.ofDays(90)));
    }
}
