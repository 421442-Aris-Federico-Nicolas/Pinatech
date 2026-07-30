package com.computerstore.service.dto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
public record TechnicalTicketResponse(Long id, String customerName, String customerEmail, Long technicianId, String technicianName, String deviceType, String brand, String model, String reportedProblem, String diagnosis, BigDecimal estimatedPrice, BigDecimal finalPrice, String status, String priority, Instant createdAt, Instant updatedAt, List<TicketAttachmentResponse> attachments) {}
