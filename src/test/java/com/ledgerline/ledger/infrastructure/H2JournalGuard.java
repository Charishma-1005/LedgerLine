package com.ledgerline.ledger.infrastructure;

import org.h2.api.Trigger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class H2JournalGuard implements Trigger {
    private String tableName;

    @Override
    public void init(Connection connection, String schemaName, String triggerName,
                     String tableName, boolean before, int type) {
        this.tableName = tableName;
    }

    @Override
    public void fire(Connection connection, Object[] oldRow, Object[] newRow) throws SQLException {
        if ("JOURNAL_TRANSACTION".equalsIgnoreCase(tableName)) {
            guardTransaction(connection, oldRow, newRow);
        } else {
            guardEntry(connection, oldRow, newRow);
        }
    }

    private void guardTransaction(Connection connection, Object[] oldRow, Object[] newRow) throws SQLException {
        if (oldRow[5] != null && !"DRAFT".equals(oldRow[5].toString())) {
            throw new SQLException("Posted journal transactions are immutable");
        }
        if (newRow != null && "POSTED".equals(newRow[5].toString())) {
            long transactionId = ((Number) newRow[0]).longValue();
            try (PreparedStatement count = connection.prepareStatement(
                    "select count(*) from journal_entry where journal_transaction_id = ?")) {
                count.setLong(1, transactionId);
                try (ResultSet rs = count.executeQuery()) {
                    rs.next();
                    if (rs.getLong(1) < 2) throw new SQLException("A posted journal transaction needs at least two entries");
                }
            }
            try (PreparedStatement balances = connection.prepareStatement("""
                    select currency_code from journal_entry where journal_transaction_id = ?
                    group by currency_code having sum(debit_amount) <> sum(credit_amount)
                    """)) {
                balances.setLong(1, transactionId);
                try (ResultSet rs = balances.executeQuery()) {
                    if (rs.next()) throw new SQLException("Journal debits and credits must balance for each currency");
                }
            }
        }
    }

    private void guardEntry(Connection connection, Object[] oldRow, Object[] newRow) throws SQLException {
        long transactionId = ((Number) (newRow == null ? oldRow : newRow)[1]).longValue();
        if (oldRow != null) assertDraft(connection, ((Number) oldRow[1]).longValue());
        assertDraft(connection, transactionId);
    }

    private void assertDraft(Connection connection, long transactionId) throws SQLException {
        try (PreparedStatement status = connection.prepareStatement(
                "select status from journal_transaction where id = ? for update")) {
            status.setLong(1, transactionId);
            try (ResultSet rs = status.executeQuery()) {
                if (!rs.next() || !"DRAFT".equals(rs.getString(1))) {
                    throw new SQLException("Entries for posted journal transactions are immutable");
                }
            }
        }
    }
}
