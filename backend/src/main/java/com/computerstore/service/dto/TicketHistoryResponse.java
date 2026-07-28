package com.computerstore.service.dto;

import java.time.Instant;

public record TicketHistoryResponse(Long id, String previousStatus, String newStatus, String comment, String changedBy, Instant changedAt) {}
