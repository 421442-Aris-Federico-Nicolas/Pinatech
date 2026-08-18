package com.computerstore.payment.gateway;

import java.math.BigDecimal;

public record RefundResult(String id, String status, BigDecimal amount) {
}
