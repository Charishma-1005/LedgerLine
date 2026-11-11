package com.ledgerline.auditor.infrastructure;

import com.ledgerline.auditor.application.AuditorQueryStore;
import com.ledgerline.auditor.application.AuditorStatementLine;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class JdbcAuditorQueryStore implements AuditorQueryStore {
    private final JdbcTemplate jdbc;

    public JdbcAuditorQueryStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<AuditorStatementLine> recentPostedStatementLines(int transactionLimit) {
        int boundedLimit = Math.max(1, Math.min(transactionLimit, 100));
        return jdbc.query("""
                with recent_transactions as (
                    select id, owner_id, transaction_type, description, posted_at
                    from journal_transaction
                    where status = 'POSTED'
                    order by posted_at desc, id desc
                    limit ?
                )
                select t.id as transaction_id, u.email_normalized as owner_email, t.posted_at,
                       t.transaction_type, t.description, e.currency_code, ledger.name as ledger_account_name,
                       e.debit_amount, e.credit_amount
                from recent_transactions t
                join app_user u on u.id = t.owner_id
                join journal_entry e on e.journal_transaction_id = t.id
                join ledger_account ledger on ledger.id = e.ledger_account_id
                order by t.posted_at desc, t.id desc, e.id
                """, (rs, row) -> mapStatementLine(rs), boundedLimit);
    }

    private AuditorStatementLine mapStatementLine(ResultSet rs) throws SQLException {
        return new AuditorStatementLine(rs.getLong("transaction_id"), rs.getString("owner_email"),
                rs.getTimestamp("posted_at").toInstant(), rs.getString("transaction_type"),
                rs.getString("description"), rs.getString("currency_code").strip(),
                rs.getString("ledger_account_name"), rs.getBigDecimal("debit_amount"),
                rs.getBigDecimal("credit_amount"));
    }
}
