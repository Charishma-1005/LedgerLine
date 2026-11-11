package com.ledgerline.account.web;

import com.ledgerline.account.domain.FinancialAccount;
import com.ledgerline.account.domain.FinancialAccountType;

import java.time.Instant;
import java.math.BigDecimal;

public record FinancialAccountView(long id, String displayName, FinancialAccountType type,
                                   String currencyCode, Instant createdAt, boolean active, BigDecimal balance) {
    public static FinancialAccountView from(FinancialAccount account, BigDecimal balance) {
        return new FinancialAccountView(account.id(), account.displayName(), account.type(),
                account.currencyCode(), account.createdAt(), account.active(), balance);
    }
}
