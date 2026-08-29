package com.computerstore.payment.config;

import com.computerstore.order.domain.BankAccountSnapshot;
import com.computerstore.order.domain.CustomerOrder;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Locale;

@ConfigurationProperties("app.payments.bank-transfer")
public record BankTransferProperties(
        boolean enabled,
        String holder,
        String taxId,
        String bankName,
        String alias,
        String cbu,
        String currency,
        Duration proofTtl
) {
    public BankTransferProperties {
        proofTtl = proofTtl == null ? Duration.ofHours(24) : proofTtl;
        if (enabled && !valid(holder, taxId, bankName, alias, cbu, currency, proofTtl)) {
            throw new IllegalStateException("Enabled bank transfer configuration is incomplete or invalid.");
        }
    }

    public boolean available() {
        return enabled && valid(holder, taxId, bankName, alias, cbu, currency, proofTtl);
    }

    public BankAccountSnapshot snapshot() {
        if (!available()) throw new IllegalStateException("Bank transfer payment is not available.");
        return new BankAccountSnapshot(holder.trim(), taxId.trim(), bankName.trim(), alias.trim(), cbu.trim(),
                currency.trim().toUpperCase(Locale.ROOT));
    }

    private static boolean valid(String holder, String taxId, String bankName, String alias, String cbu,
                                 String currency, Duration ttl) {
        return present(holder) && length(holder, 150) && present(taxId) && length(taxId, 30)
                && present(bankName) && length(bankName, 150) && present(alias) && length(alias, 100)
                && cbu != null && cbu.trim().matches("[0-9]{22}")
                && currency != null && currency.trim().equalsIgnoreCase(CustomerOrder.DEFAULT_CURRENCY)
                && ttl != null && !ttl.isNegative() && !ttl.isZero();
    }

    private static boolean present(String value) { return value != null && !value.isBlank(); }
    private static boolean length(String value, int max) { return value.trim().length() <= max; }
}
