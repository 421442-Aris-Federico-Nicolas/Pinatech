package com.computerstore.service.dto;

import com.computerstore.service.domain.UploaderRole;

import java.time.Instant;

public record TicketAttachmentResponse(Long id, String fileName, String contentType, long sizeBytes,
                                       String uploadedByName, UploaderRole uploaderRole, Instant createdAt) {}
