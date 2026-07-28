package com.computerstore.order.dto;
import java.math.BigDecimal; import java.time.Instant; import java.util.List;
public record OrderResponse(Long id,String status,BigDecimal total,Instant createdAt,Instant reservationExpiresAt,String customerName,String customerEmail,List<Item> items){public record Item(Long productId,String productName,BigDecimal unitPrice,int quantity,BigDecimal subtotal){}}
