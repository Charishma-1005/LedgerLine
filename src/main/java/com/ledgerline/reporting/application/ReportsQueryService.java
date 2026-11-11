package com.ledgerline.reporting.application;

import com.ledgerline.account.application.AuthenticatedUserIdProvider;
import com.ledgerline.account.application.FinancialAccountOverviewService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class ReportsQueryService {
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final ReportQueryStore reports;
    private final AuthenticatedUserIdProvider authenticatedUser;
    private final FinancialAccountOverviewService accounts;

    public ReportsQueryService(ReportQueryStore reports, AuthenticatedUserIdProvider authenticatedUser,
                               FinancialAccountOverviewService accounts) {
        this.reports = reports;
        this.authenticatedUser = authenticatedUser;
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public ReportsData load(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("A valid report date range is required");
        }
        long ownerId = authenticatedUser.requireUserId();
        var from = startDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        var to = endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        Map<String, CurrencyAccumulator> totals = new TreeMap<>();
        reports.operationAmounts(ownerId, from, to).forEach(amount -> {
            CurrencyAccumulator accumulator = totals.computeIfAbsent(amount.currencyCode(), ignored -> new CurrencyAccumulator());
            accumulator.transactionCount += amount.count();
            switch (amount.transactionType()) {
                case "DEPOSIT" -> accumulator.deposits = accumulator.deposits.add(amount.amount());
                case "WITHDRAWAL" -> accumulator.withdrawals = accumulator.withdrawals.add(amount.amount());
                case "TRANSFER" -> accumulator.transfers = accumulator.transfers.add(amount.amount());
                default -> { }
            }
        });
        reports.incomeExpenseAmounts(ownerId, from, to).forEach(amount -> {
            CurrencyAccumulator accumulator = totals.computeIfAbsent(amount.currencyCode(), ignored -> new CurrencyAccumulator());
            accumulator.income = amount.income();
            accumulator.expenses = amount.expenses();
        });

        Map<String, Map<YearMonth, MonthAccumulator>> months = new TreeMap<>();
        reports.monthlyActivity(ownerId, from, to).forEach(activity -> {
            YearMonth month = YearMonth.of(activity.year(), activity.month());
            MonthAccumulator accumulator = months.computeIfAbsent(activity.currencyCode(), ignored -> new TreeMap<>())
                    .computeIfAbsent(month, ignored -> new MonthAccumulator());
            switch (activity.transactionType()) {
                case "DEPOSIT" -> accumulator.deposits = accumulator.deposits.add(activity.amount());
                case "WITHDRAWAL" -> accumulator.withdrawals = accumulator.withdrawals.add(activity.amount());
                case "TRANSFER" -> accumulator.transfers = accumulator.transfers.add(activity.amount());
                default -> { }
            }
        });

        List<ReportsData.CurrencySummary> summaries = totals.entrySet().stream().map(entry -> {
            CurrencyAccumulator value = entry.getValue();
            return new ReportsData.CurrencySummary(entry.getKey(), value.income, value.expenses,
                    value.deposits, value.withdrawals, value.transfers, value.transactionCount);
        }).toList();
        List<ReportsData.TrendSeries> trends = months.entrySet().stream().map(entry ->
                new ReportsData.TrendSeries(entry.getKey(), entry.getValue().entrySet().stream().map(month ->
                        new ReportsData.MonthlyAmount(month.getKey(), month.getValue().deposits,
                                month.getValue().withdrawals, month.getValue().transfers)).toList())).toList();
        List<ReportsData.AccountBalance> accountBalances = accounts.listMine().stream().map(overview -> {
            var account = overview.account();
            return new ReportsData.AccountBalance(account.displayName(), account.type().name(),
                    account.currencyCode(), overview.ledgerBalance(), account.active());
        }).toList();

        return new ReportsData(startDate, endDate, summaries, trends, accountBalances);
    }

    private static final class CurrencyAccumulator {
        private BigDecimal income = ZERO;
        private BigDecimal expenses = ZERO;
        private BigDecimal deposits = ZERO;
        private BigDecimal withdrawals = ZERO;
        private BigDecimal transfers = ZERO;
        private long transactionCount;
    }

    private static final class MonthAccumulator {
        private BigDecimal deposits = ZERO;
        private BigDecimal withdrawals = ZERO;
        private BigDecimal transfers = ZERO;
    }
}
