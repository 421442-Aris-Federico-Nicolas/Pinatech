package com.computerstore.order.service;

import java.time.Instant;

import com.computerstore.order.repository.CustomerOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderExpirationWorker {

    private final CustomerOrderRepository orders;
    private final OrderStockService stock;

    public OrderExpirationWorker(CustomerOrderRepository orders, OrderStockService stock) {
        this.orders = orders;
        this.stock = stock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean expireNext() {
        return orders.findNextExpiredPendingIdForUpdate(Instant.now())
                .map(id -> {
                    var order = orders.findById(id).orElseThrow();
                    stock.release(order);
                    order.expire();
                    return true;
                })
                .orElse(false);
    }
}
