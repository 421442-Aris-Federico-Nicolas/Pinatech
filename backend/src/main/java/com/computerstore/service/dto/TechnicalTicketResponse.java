package com.computerstore.service.dto;
import java.time.Instant;
public record TechnicalTicketResponse(Long id, String customerName, String technicianName, String deviceType, String brand, String model, String reportedProblem, String status, String priority, Instant createdAt) {}
