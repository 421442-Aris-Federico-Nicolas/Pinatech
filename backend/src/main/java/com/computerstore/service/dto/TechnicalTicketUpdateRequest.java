package com.computerstore.service.dto;

import java.math.BigDecimal;
import com.computerstore.service.domain.TicketPriority;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TechnicalTicketUpdateRequest(
        @NotNull TicketPriority priority,
        @Size(max = 3000) String diagnosis,
        @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal estimatedPrice,
        @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal finalPrice
) {}
