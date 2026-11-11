package com.ledgerline.account.infrastructure;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SpringDataFinancialAccountRepository extends Repository<FinancialAccountEntity, Long> {
    FinancialAccountEntity saveAndFlush(FinancialAccountEntity account);

    List<FinancialAccountEntity> findAllByOwnerIdOrderByCreatedAtDescIdDesc(long ownerId);

    Optional<FinancialAccountEntity> findByIdAndOwnerId(long id, long ownerId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update FinancialAccountEntity account set account.displayName = :displayName " +
            "where account.id = :accountId and account.ownerId = :ownerId and account.active = true")
    int renameActiveOwned(@Param("accountId") long accountId,
                          @Param("ownerId") long ownerId,
                          @Param("displayName") String displayName);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update FinancialAccountEntity account set account.active = false " +
            "where account.id = :accountId and account.ownerId = :ownerId and account.active = true")
    int deactivateActiveOwned(@Param("accountId") long accountId, @Param("ownerId") long ownerId);
}
