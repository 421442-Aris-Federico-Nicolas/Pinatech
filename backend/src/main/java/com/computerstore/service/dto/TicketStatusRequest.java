package com.computerstore.service.dto;
import jakarta.validation.constraints.NotNull; import com.computerstore.service.domain.TicketStatus;
public record TicketStatusRequest(@NotNull TicketStatus status){}
