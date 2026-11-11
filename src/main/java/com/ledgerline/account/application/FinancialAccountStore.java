package com.ledgerline.account.application;

import com.ledgerline.account.domain.FinancialAccount;
import com.ledgerline.account.domain.FinancialAccountType;

import java.util.List;
import java.util.Optional;

public interface FinancialAccountStore {
    FinancialAccount createForOwner(long ownerId, String displayName, FinancialAccountType type,
                                    String currencyCode, String ledgerAccountCode);

    List<FinancialAccount> findAllForOwner(long ownerId);

    Optional<FinancialAccount> findByIdForOwner(long accountId, long ownerId);

    int renameActiveForOwner(long accountId, long ownerId, String displayName);

    int deactivateActiveForOwner(long accountId, long ownerId);
}
