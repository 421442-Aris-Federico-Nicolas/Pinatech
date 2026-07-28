package com.computerstore.service.dto;
import jakarta.validation.constraints.NotNull; import jakarta.validation.constraints.Size; import com.computerstore.service.domain.TicketStatus;
public record TicketStatusRequest(@NotNull TicketStatus status, @Size(max = 1000) String comment){}
