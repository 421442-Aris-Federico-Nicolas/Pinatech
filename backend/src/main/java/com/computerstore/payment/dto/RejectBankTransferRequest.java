package com.computerstore.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectBankTransferRequest(@NotBlank @Size(max = 1000) String reason) {}
