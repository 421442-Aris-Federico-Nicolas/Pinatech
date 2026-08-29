package com.computerstore.payment.dto;

import java.time.Instant;

public record BankTransferDetailResponse(Long orderId, Instant paymentDueAt, BankAccountResponse bankAccount,
                                         BankTransferProofResponse proof) {}
