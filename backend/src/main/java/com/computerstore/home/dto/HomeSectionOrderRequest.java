package com.computerstore.home.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record HomeSectionOrderRequest(@NotNull List<@NotNull Long> sectionIds) {}
