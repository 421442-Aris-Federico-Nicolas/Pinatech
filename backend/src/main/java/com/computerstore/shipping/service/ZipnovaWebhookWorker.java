package com.computerstore.shipping.service;

import com.computerstore.shipping.config.ZipnovaProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ZipnovaWebhookWorker {
    private static final int BATCH_SIZE = 25;
    private final ZipnovaWebhookService service; private final ZipnovaProperties properties;
    public ZipnovaWebhookWorker(ZipnovaWebhookService service, ZipnovaProperties properties) {
        this.service = service; this.properties = properties;
    }
    @Scheduled(fixedDelay = 5000)
    public void process() {
        if (!properties.available()) return;
        for (int processed = 0; processed < BATCH_SIZE; processed++) {
            var instruction = service.claim();
            if (instruction.isEmpty()) return;
            service.process(instruction.get());
        }
    }
}
