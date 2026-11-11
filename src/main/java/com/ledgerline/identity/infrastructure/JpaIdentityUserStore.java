package com.ledgerline.identity.infrastructure;

import com.ledgerline.identity.application.DuplicateEmailException;
import com.ledgerline.identity.application.IdentityUserStore;
import com.ledgerline.identity.application.IdentityUserPage;
import com.ledgerline.identity.domain.IdentityUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;

@Repository
public class JpaIdentityUserStore implements IdentityUserStore {
    private final SpringDataUserRepository repository;

    public JpaIdentityUserStore(SpringDataUserRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean existsByNormalizedEmail(String normalizedEmail) {
        return repository.existsByEmailNormalized(normalizedEmail);
    }

    @Override
    public IdentityUser saveNewUser(String email, String normalizedEmail, String passwordHash) {
        try {
            UserEntity saved = repository.saveAndFlush(
                    new UserEntity(email, normalizedEmail, passwordHash, Instant.now()));
            return saved.toDomain();
        } catch (DataIntegrityViolationException exception) {
            if (isUniqueConstraintViolation(exception)) {
                throw new DuplicateEmailException();
            }
            throw exception;
        }
    }

    @Override
    public Optional<IdentityUser> findByNormalizedEmail(String normalizedEmail) {
        return repository.findByEmailNormalized(normalizedEmail).map(UserEntity::toDomain);
    }

    @Override
    public Optional<IdentityUser> findById(long id) {
        return repository.findById(id).map(UserEntity::toDomain);
    }

    @Override
    public List<IdentityUser> findAllUsers() {
        return repository.findAllByOrderByCreatedAtAscIdAsc().stream().map(UserEntity::toDomain).toList();
    }

    @Override
    public IdentityUserPage searchUsers(String normalizedEmailQuery, int page, int size) {
        Page<UserEntity> results = repository.findByEmailNormalizedContainingIgnoreCaseOrderByCreatedAtAscIdAsc(
                normalizedEmailQuery, org.springframework.data.domain.PageRequest.of(page, size));
        return new IdentityUserPage(results.getContent().stream().map(UserEntity::toDomain).toList(),
                results.getNumber(), results.getSize(), results.getTotalElements(), results.getTotalPages());
    }

    @Override
    public List<IdentityUser> lockAllUsers() {
        return repository.findAllForUpdateById().stream().map(UserEntity::toDomain).toList();
    }

    @Override
    public int setEnabled(long id, boolean enabled) {
        return repository.updateEnabled(id, enabled);
    }

    @Override
    public long countEnabledAdmins() {
        return repository.countByRoleAndEnabled("ADMIN", true);
    }

    @Override
    @Transactional
    public boolean promoteFirstAdmin(String normalizedEmail) {
        return promoteFirstRole(normalizedEmail, "ADMIN");
    }

    @Override
    @Transactional
    public boolean promoteFirstAuditor(String normalizedEmail) {
        return promoteFirstRole(normalizedEmail, "AUDITOR");
    }

    private boolean promoteFirstRole(String normalizedEmail, String role) {
        List<UserEntity> lockedUsers = repository.findAllForUpdateById();
        if (lockedUsers.isEmpty() || lockedUsers.stream().anyMatch(user -> role.equals(user.getRole()))) {
            return false;
        }
        UserEntity target = lockedUsers.stream()
                .filter(user -> user.getEmailNormalized().equals(normalizedEmail)
                        && "USER".equals(user.getRole())
                        && user.isEnabled() && user.getPasswordHash() != null)
                .findFirst().orElse(null);
        if (target == null) return false;
        return ("ADMIN".equals(role) ? repository.promoteToAdmin(target.getId())
                : repository.promoteToAuditor(target.getId())) == 1;
    }

    private boolean isUniqueConstraintViolation(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException
                    && "23505".equals(sqlException.getSQLState())) {
                return true;
            }
        }
        return false;
    }
}
