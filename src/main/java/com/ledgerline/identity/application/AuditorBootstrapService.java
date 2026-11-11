package com.ledgerline.identity.application;

import com.ledgerline.audit.application.AuditService;
import com.ledgerline.identity.domain.EmailAddress;
import com.ledgerline.identity.domain.IdentityUser;
import com.ledgerline.identity.domain.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class AuditorBootstrapService {
    private final IdentityUserStore users;
    private final AuditService audit;

    public AuditorBootstrapService(IdentityUserStore users, AuditService audit) {
        this.users = users;
        this.audit = audit;
    }

    /** Operator-only startup action; no web endpoint can grant the auditor role. */
    @Transactional
    public boolean promoteConfiguredAuditor(String configuredEmail) {
        if (configuredEmail == null || configuredEmail.isBlank()) return false;
        String normalizedEmail = EmailAddress.of(configuredEmail).normalized();
        IdentityUser candidate = users.findByNormalizedEmail(normalizedEmail).orElse(null);
        if (candidate == null || candidate.role() != UserRole.USER
                || !candidate.enabled() || candidate.passwordHash() == null) return false;
        if (!users.promoteFirstAuditor(normalizedEmail)) return false;
        audit.recordSystem("AUDITOR_BOOTSTRAPPED", "USER", candidate.id(),
                Map.of("role", "AUDITOR", "source", "operator_startup_configuration"));
        return true;
    }
}
