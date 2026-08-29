package com.computerstore.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class BankAccountSnapshot {
    @Column(name = "bank_holder", length = 150)
    private String holder;
    @Column(name = "bank_tax_id", length = 30)
    private String taxId;
    @Column(name = "bank_name", length = 150)
    private String bankName;
    @Column(name = "bank_alias", length = 100)
    private String alias;
    @Column(name = "bank_cbu", length = 22)
    private String cbu;
    @Column(name = "bank_currency", length = 3)
    private String currency;

    protected BankAccountSnapshot() {}

    public BankAccountSnapshot(String holder, String taxId, String bankName, String alias, String cbu, String currency) {
        this.holder = holder;
        this.taxId = taxId;
        this.bankName = bankName;
        this.alias = alias;
        this.cbu = cbu;
        this.currency = currency;
    }

    public String getHolder() { return holder; }
    public String getTaxId() { return taxId; }
    public String getBankName() { return bankName; }
    public String getAlias() { return alias; }
    public String getCbu() { return cbu; }
    public String getCurrency() { return currency; }
}
