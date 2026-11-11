package com.ledgerline.identity.application;

import com.ledgerline.audit.application.AuditService;
import com.ledgerline.identity.domain.EmailAddress;
import com.ledgerline.identity.domain.IdentityUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class AdminBootstrapService {
    private final IdentityUserStore users;
    private final AuditService audit;

    public AdminBootstrapService(IdentityUserStore users, AuditService audit) {
        this.users = users;
        this.audit = audit;
    }

    /** Operator-only startup action. There is deliberately no web endpoint for this operation. */
    @Transactional
    public boolean promoteConfiguredUser(String configuredEmail) {
        if (configuredEmail == null || configuredEmail.isBlank()) return false;
        String normalizedEmail = EmailAddress.of(configuredEmail).normalized();
        IdentityUser candidate = users.findByNormalizedEmail(normalizedEmail).orElse(null);
        if (candidate == null || !candidate.enabled() || candidate.passwordHash() == null) return false;
        if (!users.promoteFirstAdmin(normalizedEmail)) return false;
        audit.recordSystem("ADMIN_BOOTSTRAPPED", "USER", candidate.id(),
                Map.of("role", "ADMIN", "source", "operator_startup_configuration"));
        return true;
    }
}
