package com.ledgerline.auditor.application;

import com.ledgerline.account.application.AuthenticatedUserIdProvider;
import com.ledgerline.audit.application.AuditEventStore;
import com.ledgerline.audit.domain.AuditEvent;
import com.ledgerline.identity.application.IdentityUserStore;
import com.ledgerline.identity.domain.UserRole;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuditorReadService {
    private final AuthenticatedUserIdProvider authenticatedUser;
    private final IdentityUserStore users;
    private final AuditEventStore auditEvents;
    private final AuditorQueryStore statements;

    public AuditorReadService(AuthenticatedUserIdProvider authenticatedUser, IdentityUserStore users,
                              AuditEventStore auditEvents, AuditorQueryStore statements) {
        this.authenticatedUser = authenticatedUser;
        this.users = users;
        this.auditEvents = auditEvents;
        this.statements = statements;
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> recentAuditEvents() {
        requireAuditor();
        return auditEvents.findRecent(100);
    }

    @Transactional(readOnly = true)
    public List<AuditorStatementLine> recentStatementLines() {
        requireAuditor();
        return statements.recentPostedStatementLines(100);
    }

    private void requireAuditor() {
        long userId = authenticatedUser.requireUserId();
        var user = users.findById(userId)
                .orElseThrow(() -> new AccessDeniedException("Auditor account is unavailable"));
        if (!user.enabled() || user.role() != UserRole.AUDITOR) {
            throw new AccessDeniedException("Auditor role is required");
        }
    }
}
