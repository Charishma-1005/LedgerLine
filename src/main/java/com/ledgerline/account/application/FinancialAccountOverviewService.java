package com.ledgerline.account.application;

import com.ledgerline.ledger.application.FinancialOperationStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Read-only account projection that derives visible balances from posted ledger entries. */
@Service
public class FinancialAccountOverviewService {
    private final FinancialAccountService accounts;
    private final FinancialOperationStore ledger;

    public FinancialAccountOverviewService(FinancialAccountService accounts, FinancialOperationStore ledger) {
        this.accounts = accounts;
        this.ledger = ledger;
    }

    @Transactional(readOnly = true)
    public List<FinancialAccountOverview> listMine() {
        return accounts.listMine().stream().map(this::withLedgerBalance).toList();
    }

    @Transactional(readOnly = true)
    public FinancialAccountOverview getMine(long accountId) {
        return withLedgerBalance(accounts.getMine(accountId));
    }

    private FinancialAccountOverview withLedgerBalance(com.ledgerline.account.domain.FinancialAccount account) {
        return new FinancialAccountOverview(account,
                ledger.assetBalance(account.ledgerAccountId(), account.currencyCode()));
    }
}
