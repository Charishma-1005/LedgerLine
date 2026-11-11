package com.ledgerline.reporting.infrastructure;

import com.ledgerline.reporting.application.ReportQueryStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class JdbcReportQueryStore implements ReportQueryStore {
    private static final String REPORTABLE_TRANSACTION = """
            t.owner_id = ? and t.status = 'POSTED'
              and t.reversal_of_transaction_id is null
              and not exists (select 1 from journal_transaction reversal
                              where reversal.reversal_of_transaction_id = t.id)
              and t.occurred_at >= ? and t.occurred_at < ?
            """;

    private final JdbcTemplate jdbc;

    public JdbcReportQueryStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<OperationAmount> operationAmounts(long ownerId, Instant fromInclusive, Instant toExclusive) {
        return jdbc.query("""
                select e.currency_code, t.transaction_type,
                       sum(e.debit_amount) as amount, count(distinct t.id) as transaction_count
                from journal_transaction t
                join journal_entry e on e.journal_transaction_id = t.id
                where %s and t.transaction_type in ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER')
                group by e.currency_code, t.transaction_type
                order by e.currency_code, t.transaction_type
                """.formatted(REPORTABLE_TRANSACTION), (rs, row) -> new OperationAmount(
                rs.getString("currency_code").strip(), rs.getString("transaction_type"),
                rs.getBigDecimal("amount"), rs.getLong("transaction_count")),
                ownerId, Timestamp.from(fromInclusive), Timestamp.from(toExclusive));
    }

    @Override
    public List<IncomeExpenseAmount> incomeExpenseAmounts(long ownerId, Instant fromInclusive, Instant toExclusive) {
        return jdbc.query("""
                select e.currency_code,
                       sum(case when ledger.account_type = 'INCOME'
                                then e.credit_amount - e.debit_amount else 0 end) as income,
                       sum(case when ledger.account_type = 'EXPENSE'
                                then e.debit_amount - e.credit_amount else 0 end) as expenses
                from journal_transaction t
                join journal_entry e on e.journal_transaction_id = t.id
                join ledger_account ledger on ledger.id = e.ledger_account_id and ledger.owner_id = t.owner_id
                where %s and ledger.account_type in ('INCOME', 'EXPENSE')
                group by e.currency_code
                order by e.currency_code
                """.formatted(REPORTABLE_TRANSACTION), (rs, row) -> new IncomeExpenseAmount(
                rs.getString("currency_code").strip(), rs.getBigDecimal("income"), rs.getBigDecimal("expenses")),
                ownerId, Timestamp.from(fromInclusive), Timestamp.from(toExclusive));
    }

    @Override
    public List<MonthlyActivity> monthlyActivity(long ownerId, Instant fromInclusive, Instant toExclusive) {
        return jdbc.query("""
                select e.currency_code,
                       extract(year from (t.occurred_at at time zone 'UTC')) as activity_year,
                       extract(month from (t.occurred_at at time zone 'UTC')) as activity_month,
                       t.transaction_type, sum(e.debit_amount) as amount
                from journal_transaction t
                join journal_entry e on e.journal_transaction_id = t.id
                where %s and t.transaction_type in ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER')
                group by e.currency_code, extract(year from (t.occurred_at at time zone 'UTC')),
                         extract(month from (t.occurred_at at time zone 'UTC')), t.transaction_type
                order by e.currency_code, activity_year, activity_month, t.transaction_type
                """.formatted(REPORTABLE_TRANSACTION), (rs, row) -> new MonthlyActivity(
                rs.getString("currency_code").strip(), rs.getInt("activity_year"), rs.getInt("activity_month"),
                rs.getString("transaction_type"), rs.getBigDecimal("amount")),
                ownerId, Timestamp.from(fromInclusive), Timestamp.from(toExclusive));
    }
}
