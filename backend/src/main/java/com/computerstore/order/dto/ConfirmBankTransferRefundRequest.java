package com.computerstore.order.dto;

import jakarta.validation.constraints.Size;

public record ConfirmBankTransferRefundRequest(@Size(max = 200) String reference) {
}
