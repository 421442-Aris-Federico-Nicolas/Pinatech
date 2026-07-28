package com.computerstore.order.service;

import com.computerstore.order.config.OrderProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrderExpirationScheduler {

    private final OrderExpirationWorker worker;
    private final OrderProperties properties;

    public OrderExpirationScheduler(OrderExpirationWorker worker, OrderProperties properties) {
        this.worker = worker;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.orders.expiration-check-interval-ms:60000}")
    public void releaseExpiredReservations() {
        for (int count = 0; count < properties.expirationBatchSize() && worker.expireNext(); count++) {
            // Each reservation is committed separately so locks stay short-lived.
        }
    }
}
