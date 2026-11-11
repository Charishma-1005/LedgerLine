package com.ledgerline.ledger.infrastructure;

import com.ledgerline.ledger.application.FinancialOperationStore;
import com.ledgerline.ledger.domain.PostedTransaction;
import com.ledgerline.ledger.domain.TransactionType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcFinancialOperationStore implements FinancialOperationStore {
    private final JdbcTemplate jdbc;

    public JdbcFinancialOperationStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void lockOwner(long ownerId) {
        jdbc.queryForObject("select id from app_user where id = ? for update", Long.class, ownerId);
    }

    @Override
    public Optional<IdempotentResult> findIdempotent(long ownerId, String key) {
        return jdbc.query("select journal_transaction_id, request_hash from idempotency_record " +
                        "where owner_id = ? and idempotency_key = ?",
                rs -> rs.next() ? Optional.of(new IdempotentResult(rs.getLong(1), rs.getString(2).strip()))
                        : Optional.empty(), ownerId, key);
    }

    @Override
    public List<LockedAccount> lockOwnedAccounts(long ownerId, List<Long> accountIds) {
        List<Long> sorted = accountIds.stream().distinct().sorted().toList();
        if (sorted.isEmpty()) return List.of();
        String marks = String.join(",", java.util.Collections.nCopies(sorted.size(), "?"));
        Object[] args = new Object[sorted.size() + 1];
        args[0] = ownerId;
        for (int i = 0; i < sorted.size(); i++) args[i + 1] = sorted.get(i);
        return jdbc.query("select id, owner_id, ledger_account_id, currency_code, active from financial_account " +
                        "where owner_id = ? and id in (" + marks + ") order by id for update",
                (rs, row) -> new LockedAccount(rs.getLong("id"), rs.getLong("owner_id"),
                        rs.getLong("ledger_account_id"), rs.getString("currency_code").strip(),
                        rs.getBoolean("active")), args);
    }

    @Override
    public BigDecimal assetBalance(long ledgerAccountId, String currencyCode) {
        return jdbc.queryForObject("select coalesce(sum(e.debit_amount - e.credit_amount), 0) " +
                        "from journal_entry e join journal_transaction t on t.id = e.journal_transaction_id " +
                        "where e.ledger_account_id = ? and e.currency_code = ? and t.status = 'POSTED'",
                BigDecimal.class, ledgerAccountId, currencyCode);
    }

    @Override
    public long ensureExternalEquityAccount(long ownerId, long financialAccountId, String currencyCode) {
        // One deterministic counterpart per financial account means concurrent cash flows serialize
        // on that account row and the existing (owner_id, code) constraint protects creation.
        String code = "EXT-FLOW-" + financialAccountId;
        List<Long> existing = jdbc.query("select id from ledger_account where owner_id = ? and code = ?",
                (rs, row) -> rs.getLong(1), ownerId, code);
        if (!existing.isEmpty()) return existing.getFirst();
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    insert into ledger_account(owner_id, code, name, account_type, currency_code, is_system, created_at)
                    values (?, ?, 'External cash flow', 'EQUITY', ?, false, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, ownerId);
            statement.setString(2, code);
            statement.setString(3, currencyCode);
            statement.setTimestamp(4, Timestamp.from(Instant.now()));
            return statement;
        }, key);
        Number generated = (Number) key.getKeyList().getFirst().get("id");
        if (generated == null) throw new IllegalStateException("Ledger account ID was not generated");
        return generated.longValue();
    }

    @Override
    public long insertDraftTransaction(long ownerId, TransactionType type, String description) {
        KeyHolder key = new GeneratedKeyHolder();
        Instant now = Instant.now();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    insert into journal_transaction(owner_id, description, status, occurred_at, posted_at,
                                                   created_at, transaction_type)
                    values (?, ?, 'DRAFT', ?, null, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, ownerId);
            statement.setString(2, description);
            statement.setTimestamp(3, Timestamp.from(now));
            statement.setTimestamp(4, Timestamp.from(now));
            statement.setString(5, type.name());
            return statement;
        }, key);
        Number generated = (Number) key.getKeyList().getFirst().get("id");
        if (generated == null) throw new IllegalStateException("Transaction ID was not generated");
        return generated.longValue();
    }

    @Override
    public void insertEntry(long transactionId, long ledgerAccountId, String currencyCode,
                            BigDecimal debit, BigDecimal credit) {
        jdbc.update("""
                insert into journal_entry(journal_transaction_id, ledger_account_id, currency_code,
                                          debit_amount, credit_amount, created_at)
                values (?, ?, ?, ?, ?, ?)
                """, transactionId, ledgerAccountId, currencyCode, debit, credit, Timestamp.from(Instant.now()));
    }

    @Override
    public void insertIdempotency(long ownerId, String key, String requestHash, long transactionId) {
        jdbc.update("""
                insert into idempotency_record(owner_id, idempotency_key, request_hash,
                                               journal_transaction_id, created_at)
                values (?, ?, ?, ?, ?)
                """, ownerId, key, requestHash, transactionId, Timestamp.from(Instant.now()));
    }

    @Override
    public void markTransactionPosted(long transactionId) {
        int updated = jdbc.update("update journal_transaction set status = 'POSTED', posted_at = ? " +
                        "where id = ? and status = 'DRAFT'", Timestamp.from(Instant.now()), transactionId);
        if (updated != 1) throw new IllegalStateException("Journal transaction could not be posted");
    }

    @Override
    public List<PostedTransaction> findHistory(long ownerId) {
        RowMapper<PostedTransaction> mapper = (rs, row) -> new PostedTransaction(
                rs.getLong("id"), TransactionType.valueOf(rs.getString("transaction_type")),
                rs.getString("description"), rs.getBigDecimal("amount"), rs.getString("currency_code").strip(),
                rs.getTimestamp("occurred_at").toInstant());
        return jdbc.query("""
                select t.id, t.transaction_type, t.description, t.occurred_at,
                       sum(e.debit_amount) as amount, e.currency_code as currency_code
                from journal_transaction t
                join journal_entry e on e.journal_transaction_id = t.id
                where t.owner_id = ? and t.status = 'POSTED'
                group by t.id, t.transaction_type, t.description, t.occurred_at, e.currency_code
                order by t.occurred_at desc, t.id desc
                """, mapper, ownerId);
    }
}
