package com.computerstore.guest.dto;

import com.computerstore.order.dto.OrderResponse;
import java.util.UUID;

public record GuestOrderResponse(UUID publicId, OrderResponse order, String accessToken) {}
