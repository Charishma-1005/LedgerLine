package com.ledgerline.reporting.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public record ReportsData(LocalDate startDate, LocalDate endDate,
                          List<CurrencySummary> currencies,
                          List<TrendSeries> monthlyTrends,
                          List<AccountBalance> accounts) {
    public ReportsData {
        currencies = List.copyOf(currencies);
        monthlyTrends = List.copyOf(monthlyTrends);
        accounts = List.copyOf(accounts);
    }

    public record CurrencySummary(String currencyCode, BigDecimal income, BigDecimal expenses,
                                  BigDecimal deposits, BigDecimal withdrawals, BigDecimal transfers,
                                  long transactionCount) { }

    public record TrendSeries(String currencyCode, List<MonthlyAmount> months) {
        public TrendSeries { months = List.copyOf(months); }
    }

    public record MonthlyAmount(YearMonth month, BigDecimal deposits,
                                BigDecimal withdrawals, BigDecimal transfers) { }

    public record AccountBalance(String displayName, String type, String currencyCode,
                                 BigDecimal balance, boolean active) { }
}
