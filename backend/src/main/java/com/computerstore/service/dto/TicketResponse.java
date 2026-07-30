package com.computerstore.service.dto;
import java.time.Instant;
import java.util.List;
public record TicketResponse(Long id,String deviceType,String brand,String model,String reportedProblem,String status,Instant createdAt,List<TicketAttachmentResponse> attachments){}
