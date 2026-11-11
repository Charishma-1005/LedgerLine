package com.ledgerline.reporting.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface ReportQueryStore {
    record OperationAmount(String currencyCode, String transactionType, BigDecimal amount, long count) { }
    record IncomeExpenseAmount(String currencyCode, BigDecimal income, BigDecimal expenses) { }
    record MonthlyActivity(String currencyCode, int year, int month, String transactionType, BigDecimal amount) { }

    List<OperationAmount> operationAmounts(long ownerId, Instant fromInclusive, Instant toExclusive);
    List<IncomeExpenseAmount> incomeExpenseAmounts(long ownerId, Instant fromInclusive, Instant toExclusive);
    List<MonthlyActivity> monthlyActivity(long ownerId, Instant fromInclusive, Instant toExclusive);
}
