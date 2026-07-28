package com.computerstore.service.dto;
import jakarta.validation.constraints.*;
public record CreateTicketRequest(@NotBlank @Size(max=100) String deviceType,@NotBlank @Size(max=100) String brand,@NotBlank @Size(max=150) String model,@Size(max=150) String serialNumber,@NotBlank @Size(max=2000) String reportedProblem){}
