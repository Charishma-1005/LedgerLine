package com.ledgerline.account.infrastructure;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface SpringDataLedgerAccountRepository extends Repository<LedgerAccountEntity, Long> {
    LedgerAccountEntity saveAndFlush(LedgerAccountEntity ledgerAccount);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update LedgerAccountEntity ledger set ledger.name = :name " +
            "where ledger.id = :ledgerId and ledger.ownerId = :ownerId and ledger.system = false")
    int renameOwned(@Param("ledgerId") long ledgerId, @Param("ownerId") long ownerId,
                    @Param("name") String name);
}
