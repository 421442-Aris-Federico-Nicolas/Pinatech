package com.computerstore.catalog.dto;

public record ProductImageResponse(Long id, String contentUrl, String altText, String originalFilename, int displayOrder) {}
