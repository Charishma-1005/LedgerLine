package com.ledgerline.account.application;

import com.ledgerline.audit.application.AuditService;
import com.ledgerline.account.domain.FinancialAccount;
import com.ledgerline.account.domain.FinancialAccountType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class FinancialAccountService {
    private final FinancialAccountStore accounts;
    private final AuthenticatedUserIdProvider authenticatedUser;
    private final AuditService audit;

    public FinancialAccountService(FinancialAccountStore accounts,
                                   AuthenticatedUserIdProvider authenticatedUser,
                                   AuditService audit) {
        this.accounts = accounts;
        this.authenticatedUser = authenticatedUser;
        this.audit = audit;
    }

    @Transactional
    public FinancialAccount create(String displayName, FinancialAccountType type, String currencyCode) {
        long ownerId = authenticatedUser.requireUserId();
        String safeName = normalizeDisplayName(displayName);
        String safeCurrency = normalizeCurrencyCode(currencyCode);
        Objects.requireNonNull(type, "Account type is required");
        String ledgerCode = "FA-" + UUID.randomUUID();
        FinancialAccount created = accounts.createForOwner(ownerId, safeName, type, safeCurrency, ledgerCode);
        audit.recordAuthenticated("ACCOUNT_CREATED", "FINANCIAL_ACCOUNT", created.id(),
                java.util.Map.of("account_type", type.name(), "currency", safeCurrency));
        return created;
    }

    @Transactional(readOnly = true)
    public List<FinancialAccount> listMine() {
        return accounts.findAllForOwner(authenticatedUser.requireUserId());
    }

    @Transactional(readOnly = true)
    public FinancialAccount getMine(long accountId) {
        return accounts.findByIdForOwner(accountId, authenticatedUser.requireUserId())
                .orElseThrow(FinancialAccountNotFoundException::new);
    }

    @Transactional
    public FinancialAccount renameMine(long accountId, String displayName) {
        long ownerId = authenticatedUser.requireUserId();
        String safeName = normalizeDisplayName(displayName);
        int updated = accounts.renameActiveForOwner(accountId, ownerId, safeName);
        if (updated == 0) {
            FinancialAccount existing = accounts.findByIdForOwner(accountId, ownerId)
                    .orElseThrow(FinancialAccountNotFoundException::new);
            if (!existing.active()) {
                throw new FinancialAccountClosedException();
            }
            throw new FinancialAccountNotFoundException();
        }
        return getForOwner(accountId, ownerId);
    }

    @Transactional
    public FinancialAccount closeMine(long accountId) {
        long ownerId = authenticatedUser.requireUserId();
        int deactivated = accounts.deactivateActiveForOwner(accountId, ownerId);
        if (deactivated == 0) {
            // Closing an already closed account is safe and idempotent for its owner.
            return accounts.findByIdForOwner(accountId, ownerId)
                    .orElseThrow(FinancialAccountNotFoundException::new);
        }
        audit.recordAuthenticated("ACCOUNT_CLOSED", "FINANCIAL_ACCOUNT", accountId,
                java.util.Map.of("account_id", accountId));
        return getForOwner(accountId, ownerId);
    }

    private FinancialAccount getForOwner(long accountId, long ownerId) {
        return accounts.findByIdForOwner(accountId, ownerId)
                .orElseThrow(FinancialAccountNotFoundException::new);
    }

    private String normalizeDisplayName(String displayName) {
        if (displayName == null) {
            throw new IllegalArgumentException("Display name is required");
        }
        String normalized = displayName.strip();
        if (normalized.isBlank() || normalized.length() > 120) {
            throw new IllegalArgumentException("Display name must be between 1 and 120 characters");
        }
        return normalized;
    }

    private String normalizeCurrencyCode(String currencyCode) {
        if (currencyCode == null || !currencyCode.equals(currencyCode.toUpperCase(Locale.ROOT))) {
            throw new InvalidCurrencyCodeException();
        }
        try {
            return Currency.getInstance(currencyCode).getCurrencyCode();
        } catch (IllegalArgumentException exception) {
            throw new InvalidCurrencyCodeException();
        }
    }
}
