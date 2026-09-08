package com.computerstore.order.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record OrderSummaryResponse(long soldOrders, BigDecimal revenue, BigDecimal averageTicket,
        long activeOrders, Map<String, Long> statusCounts, List<SalesDay> salesChart,
        List<OrderResponse> recentOrders) {
    public record SalesDay(String label, BigDecimal total, double height) {}
}
