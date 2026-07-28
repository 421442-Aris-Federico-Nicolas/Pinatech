package com.computerstore.order.dto;
import jakarta.validation.constraints.NotNull;
import com.computerstore.order.domain.OrderStatus;
public record OrderStatusRequest(@NotNull OrderStatus status) {}
