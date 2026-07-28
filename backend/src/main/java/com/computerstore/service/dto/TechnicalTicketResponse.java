package com.computerstore.service.dto;
import java.math.BigDecimal;
import java.time.Instant;
public record TechnicalTicketResponse(Long id, String customerName, String customerEmail, Long technicianId, String technicianName, String deviceType, String brand, String model, String serialNumber, String reportedProblem, String diagnosis, BigDecimal estimatedPrice, BigDecimal finalPrice, String status, String priority, Instant createdAt, Instant updatedAt) {}
