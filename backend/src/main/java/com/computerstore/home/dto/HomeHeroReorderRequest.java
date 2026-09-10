package com.computerstore.home.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record HomeHeroReorderRequest(
        @NotNull @Size(min = 1, max = 5) List<@NotNull Long> slideIds) {}