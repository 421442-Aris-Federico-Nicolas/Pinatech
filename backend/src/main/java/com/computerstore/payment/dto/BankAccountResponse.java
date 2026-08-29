package com.computerstore.payment.dto;

import com.computerstore.order.domain.BankAccountSnapshot;

public record BankAccountResponse(String holder, String taxId, String bankName, String alias, String cbu,
                                  String currency) {
    public static BankAccountResponse from(BankAccountSnapshot account) {
        return new BankAccountResponse(account.getHolder(), account.getTaxId(), account.getBankName(),
                account.getAlias(), account.getCbu(), account.getCurrency());
    }
}
