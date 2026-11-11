package com.ledgerline.account.infrastructure;

import com.ledgerline.account.application.FinancialAccountStore;
import com.ledgerline.account.domain.FinancialAccount;
import com.ledgerline.account.domain.FinancialAccountType;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class JpaFinancialAccountStore implements FinancialAccountStore {
    private final SpringDataFinancialAccountRepository financialAccounts;
    private final SpringDataLedgerAccountRepository ledgerAccounts;

    public JpaFinancialAccountStore(SpringDataFinancialAccountRepository financialAccounts,
                                    SpringDataLedgerAccountRepository ledgerAccounts) {
        this.financialAccounts = financialAccounts;
        this.ledgerAccounts = ledgerAccounts;
    }

    @Override
    public FinancialAccount createForOwner(long ownerId, String displayName, FinancialAccountType type,
                                           String currencyCode, String ledgerAccountCode) {
        Instant now = Instant.now();
        LedgerAccountEntity ledgerAccount = ledgerAccounts.saveAndFlush(
                new LedgerAccountEntity(ownerId, ledgerAccountCode, displayName, "ASSET", currencyCode, now));
        return financialAccounts.saveAndFlush(
                new FinancialAccountEntity(ownerId, ledgerAccount, displayName, type, currencyCode, now)).toDomain();
    }

    @Override
    public List<FinancialAccount> findAllForOwner(long ownerId) {
        return financialAccounts.findAllByOwnerIdOrderByCreatedAtDescIdDesc(ownerId)
                .stream().map(FinancialAccountEntity::toDomain).toList();
    }

    @Override
    public Optional<FinancialAccount> findByIdForOwner(long accountId, long ownerId) {
        return financialAccounts.findByIdAndOwnerId(accountId, ownerId).map(FinancialAccountEntity::toDomain);
    }

    @Override
    public int renameActiveForOwner(long accountId, long ownerId, String displayName) {
        FinancialAccountEntity account = financialAccounts.findByIdAndOwnerId(accountId, ownerId).orElse(null);
        if (account == null || !account.toDomain().active()) {
            return 0;
        }

        long ledgerAccountId = account.ledgerAccountId();
        int updated = financialAccounts.renameActiveOwned(accountId, ownerId, displayName);
        if (updated == 0) {
            return 0;
        }
        int ledgerRenamed = ledgerAccounts.renameOwned(ledgerAccountId, ownerId, displayName);
        if (ledgerRenamed != 1) {
            throw new IllegalStateException("Linked ledger account could not be renamed");
        }
        return updated;
    }

    @Override
    public int deactivateActiveForOwner(long accountId, long ownerId) {
        return financialAccounts.deactivateActiveOwned(accountId, ownerId);
    }
}
