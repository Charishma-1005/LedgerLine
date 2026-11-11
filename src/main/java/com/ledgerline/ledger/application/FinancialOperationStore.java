package com.ledgerline.ledger.application;

import com.ledgerline.ledger.domain.PostedTransaction;
import com.ledgerline.ledger.domain.TransactionType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface FinancialOperationStore {
    record LockedAccount(long id, long ownerId, long ledgerAccountId, String currencyCode, boolean active) { }
    record IdempotentResult(long transactionId, String requestHash) { }

    void lockOwner(long ownerId);
    Optional<IdempotentResult> findIdempotent(long ownerId, String key);
    List<LockedAccount> lockOwnedAccounts(long ownerId, List<Long> accountIds);
    BigDecimal assetBalance(long ledgerAccountId, String currencyCode);
    long ensureExternalEquityAccount(long ownerId, long financialAccountId, String currencyCode);
    long insertDraftTransaction(long ownerId, TransactionType type, String description);
    void insertEntry(long transactionId, long ledgerAccountId, String currencyCode,
                     BigDecimal debit, BigDecimal credit);
    void insertIdempotency(long ownerId, String key, String requestHash, long transactionId);
    void markTransactionPosted(long transactionId);
    List<PostedTransaction> findHistory(long ownerId);
}
