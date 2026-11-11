package com.ledgerline.dashboard.application;

import com.ledgerline.account.application.FinancialAccountOverviewService;
import com.ledgerline.ledger.application.FinancialOperationService;
import com.ledgerline.ledger.domain.PostedTransaction;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class DashboardQueryService {
    private final FinancialAccountOverviewService accounts;
    private final FinancialOperationService operations;

    public DashboardQueryService(FinancialAccountOverviewService accounts, FinancialOperationService operations) {
        this.accounts = accounts;
        this.operations = operations;
    }

    @Transactional(readOnly = true)
    public DashboardData loadForCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        List<DashboardData.AccountSummary> accountSummaries = accounts.listMine().stream()
                .map(overview -> {
                    var account = overview.account();
                    return new DashboardData.AccountSummary(account.id(), account.displayName(),
                            account.type().name(), account.currencyCode(), overview.ledgerBalance(), account.active());
                })
                .toList();

        Map<String, BigDecimal> totals = new TreeMap<>();
        accountSummaries.forEach(account -> totals.merge(account.currencyCode(), account.balance(), BigDecimal::add));

        List<DashboardData.TransactionSummary> recent = operations.history().stream()
                .limit(6)
                .map(this::toSummary)
                .toList();

        return new DashboardData(email, initials(email), accountSummaries,
                totals.entrySet().stream().map(entry -> new DashboardData.CurrencyBalance(entry.getKey(), entry.getValue())).toList(),
                recent);
    }

    private DashboardData.TransactionSummary toSummary(PostedTransaction transaction) {
        return new DashboardData.TransactionSummary(transaction.id(), transaction.type().name(),
                transaction.description(), transaction.amount(), transaction.currencyCode(),
                transaction.occurredAt(), "Posted");
    }

    private String initials(String email) {
        String localPart = email.substring(0, email.indexOf('@'));
        String[] parts = localPart.split("[._-]+", 2);
        if (parts.length > 1 && !parts[1].isBlank()) {
            return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase(java.util.Locale.ROOT);
        }
        return localPart.substring(0, Math.min(2, localPart.length())).toUpperCase(java.util.Locale.ROOT);
    }
}
