package com.computerstore.catalog.dto;

public record ProductSpecificationResponse(
        Long id,
        String groupName,
        String name,
        String value,
        boolean highlighted,
        int displayOrder
) {}
