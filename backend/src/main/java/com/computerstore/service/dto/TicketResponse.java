package com.computerstore.service.dto;
import java.time.Instant;
public record TicketResponse(Long id,String deviceType,String brand,String model,String serialNumber,String reportedProblem,String status,Instant createdAt){}
