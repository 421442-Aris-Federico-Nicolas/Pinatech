package com.computerstore.service.dto;
import jakarta.validation.constraints.NotNull;
public record AssignTechnicianRequest(@NotNull Long technicianId) {}
