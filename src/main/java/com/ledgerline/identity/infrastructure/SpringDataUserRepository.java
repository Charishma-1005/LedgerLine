package com.ledgerline.identity.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface SpringDataUserRepository extends JpaRepository<UserEntity, Long> {
    boolean existsByEmailNormalized(String emailNormalized);

    Optional<UserEntity> findByEmailNormalized(String emailNormalized);

    List<UserEntity> findAllByOrderByCreatedAtAscIdAsc();

    Page<UserEntity> findByEmailNormalizedContainingIgnoreCaseOrderByCreatedAtAscIdAsc(
            String emailNormalized, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from UserEntity user order by user.id")
    List<UserEntity> findAllForUpdateById();

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update UserEntity user set user.enabled = :enabled where user.id = :id")
    int updateEnabled(@Param("id") long id, @Param("enabled") boolean enabled);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update UserEntity user set user.role = 'ADMIN' where user.id = :id")
    int promoteToAdmin(@Param("id") long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update UserEntity user set user.role = 'AUDITOR' where user.id = :id")
    int promoteToAuditor(@Param("id") long id);

    long countByRoleAndEnabled(String role, boolean enabled);
}
