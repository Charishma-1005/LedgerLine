package com.ledgerline.ledger.application;

import com.ledgerline.account.application.AuthenticatedUserIdProvider;
import com.ledgerline.audit.application.AuditService;
import com.ledgerline.ledger.domain.PostedTransaction;
import com.ledgerline.ledger.domain.TransactionType;
import com.ledgerline.shared.domain.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Service
public class FinancialOperationService {
    private final FinancialOperationStore store;
    private final AuthenticatedUserIdProvider authenticatedUser;
    private final AuditService audit;

    public FinancialOperationService(FinancialOperationStore store, AuthenticatedUserIdProvider authenticatedUser,
                                     AuditService audit) {
        this.store = store;
        this.authenticatedUser = authenticatedUser;
        this.audit = audit;
    }

    @Transactional
    public long deposit(long accountId, BigDecimal amount, String currency, String idempotencyKey) {
        return execute(TransactionType.DEPOSIT, accountId, null, amount, currency, idempotencyKey);
    }

    @Transactional
    public long withdraw(long accountId, BigDecimal amount, String currency, String idempotencyKey) {
        return execute(TransactionType.WITHDRAWAL, accountId, null, amount, currency, idempotencyKey);
    }

    @Transactional
    public long transfer(long sourceAccountId, long destinationAccountId, BigDecimal amount,
                         String currency, String idempotencyKey) {
        return execute(TransactionType.TRANSFER, sourceAccountId, destinationAccountId,
                amount, currency, idempotencyKey);
    }

    @Transactional(readOnly = true)
    public List<PostedTransaction> history() {
        return store.findHistory(authenticatedUser.requireUserId());
    }

    private long execute(TransactionType type, long firstAccountId, Long secondAccountId,
                         BigDecimal rawAmount, String currencyCode, String idempotencyKey) {
        long ownerId = authenticatedUser.requireUserId();
        String key = validateKey(idempotencyKey);
        String currency = validateCurrency(currencyCode);
        Money money;
        try {
            money = Money.of(rawAmount, currency);
        } catch (RuntimeException exception) {
            throw new FinancialOperationException("Amount or currency is invalid", exception);
        }
        if (money.amount().signum() <= 0) throw new FinancialOperationException("Amount must be greater than zero");
        if (type == TransactionType.TRANSFER && (secondAccountId == null || firstAccountId == secondAccountId)) {
            throw new FinancialOperationException("Choose two different accounts for a transfer");
        }

        String requestHash = hash(type + "|" + firstAccountId + "|" + secondAccountId + "|"
                + money.currency().getCurrencyCode() + "|" + money.amount().toPlainString());
        // This per-owner row lock makes idempotency check-and-post serial for the owner. Account
        // locks remain explicitly ordered to protect balances and avoid deadlocks if the lock is
        // later narrowed to account scope.
        store.lockOwner(ownerId);
        var previous = store.findIdempotent(ownerId, key);
        if (previous.isPresent()) {
            if (!MessageDigest.isEqual(previous.get().requestHash().getBytes(StandardCharsets.US_ASCII),
                    requestHash.getBytes(StandardCharsets.US_ASCII))) throw new IdempotencyConflictException();
            return previous.get().transactionId();
        }

        List<Long> ids = secondAccountId == null ? List.of(firstAccountId)
                : List.of(firstAccountId, secondAccountId);
        List<FinancialOperationStore.LockedAccount> locked = store.lockOwnedAccounts(ownerId, ids);
        if (locked.size() != ids.stream().distinct().count() || locked.stream().anyMatch(account -> !account.active())) {
            throw new FinancialAccountUnavailableException();
        }
        var first = locked.stream().filter(account -> account.id() == firstAccountId).findFirst()
                .orElseThrow(FinancialAccountUnavailableException::new);
        if (!first.currencyCode().equals(currency)) throw new FinancialOperationException("Currency does not match the account");
        FinancialOperationStore.LockedAccount second = null;
        if (secondAccountId != null) {
            second = locked.stream().filter(account -> account.id() == secondAccountId).findFirst()
                    .orElseThrow(FinancialAccountUnavailableException::new);
            if (!second.currencyCode().equals(currency)) throw new FinancialOperationException("Transfer accounts must use the same currency");
        }

        if (type != TransactionType.DEPOSIT) {
            BigDecimal available = store.assetBalance(first.ledgerAccountId(), currency);
            if (available.compareTo(money.amount()) < 0) throw new InsufficientFundsException();
        }

        long transactionId = store.insertDraftTransaction(ownerId, type, description(type));
        if (type == TransactionType.DEPOSIT) {
            long equity = store.ensureExternalEquityAccount(ownerId, first.id(), currency);
            store.insertEntry(transactionId, first.ledgerAccountId(), currency, money.amount(), BigDecimal.ZERO);
            store.insertEntry(transactionId, equity, currency, BigDecimal.ZERO, money.amount());
        } else if (type == TransactionType.WITHDRAWAL) {
            long equity = store.ensureExternalEquityAccount(ownerId, first.id(), currency);
            store.insertEntry(transactionId, equity, currency, money.amount(), BigDecimal.ZERO);
            store.insertEntry(transactionId, first.ledgerAccountId(), currency, BigDecimal.ZERO, money.amount());
        } else {
            store.insertEntry(transactionId, second.ledgerAccountId(), currency, money.amount(), BigDecimal.ZERO);
            store.insertEntry(transactionId, first.ledgerAccountId(), currency, BigDecimal.ZERO, money.amount());
        }
        store.markTransactionPosted(transactionId);
        store.insertIdempotency(ownerId, key, requestHash, transactionId);
        java.util.Map<String, String> metadata = new java.util.LinkedHashMap<>();
        metadata.put("currency", currency);
        metadata.put("amount", money.amount().toPlainString());
        if (second == null) {
            metadata.put("account_id", Long.toString(first.id()));
        } else {
            metadata.put("source_account_id", Long.toString(first.id()));
            metadata.put("destination_account_id", Long.toString(second.id()));
        }
        audit.recordFinancialOperation(transactionId, type.name(), metadata);
        return transactionId;
    }

    private String validateKey(String key) {
        if (key == null || key.isBlank() || key.length() > 200) {
            throw new FinancialOperationException("A unique idempotency key of at most 200 characters is required");
        }
        return key.strip();
    }

    private String validateCurrency(String code) {
        if (code == null || !code.equals(code.toUpperCase(Locale.ROOT))) {
            throw new FinancialOperationException("Currency must be an uppercase ISO 4217 code");
        }
        try {
            return java.util.Currency.getInstance(code).getCurrencyCode();
        } catch (IllegalArgumentException exception) {
            throw new FinancialOperationException("Currency is not supported", exception);
        }
    }

    private String description(TransactionType type) {
        return switch (type) {
            case DEPOSIT -> "Deposit";
            case WITHDRAWAL -> "Withdrawal";
            case TRANSFER -> "Transfer";
            case LEGACY -> "Legacy transaction";
        };
    }

    private String hash(String canonical) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
