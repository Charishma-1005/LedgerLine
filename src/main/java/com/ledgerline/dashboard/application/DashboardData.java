package com.ledgerline.dashboard.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record DashboardData(String email, String initials,
                            List<AccountSummary> accounts,
                            List<CurrencyBalance> totalBalances,
                            List<TransactionSummary> recentTransactions) {
    public record AccountSummary(long id, String displayName, String type, String currencyCode,
                                 BigDecimal balance, boolean active) { }

    public record CurrencyBalance(String currencyCode, BigDecimal amount) { }

    public record TransactionSummary(long id, String type, String description, BigDecimal amount,
                                     String currencyCode, Instant occurredAt, String status) { }
}
