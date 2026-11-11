package com.ledgerline.identity.application;

import com.ledgerline.identity.domain.IdentityUser;

import java.util.Optional;
import java.util.List;

public interface IdentityUserStore {
    boolean existsByNormalizedEmail(String normalizedEmail);

    IdentityUser saveNewUser(String email, String normalizedEmail, String passwordHash);

    Optional<IdentityUser> findByNormalizedEmail(String normalizedEmail);

    Optional<IdentityUser> findById(long id);

    List<IdentityUser> findAllUsers();

    IdentityUserPage searchUsers(String normalizedEmailQuery, int page, int size);

    List<IdentityUser> lockAllUsers();

    int setEnabled(long id, boolean enabled);

    long countEnabledAdmins();

    boolean promoteFirstAdmin(String normalizedEmail);

    boolean promoteFirstAuditor(String normalizedEmail);
}
