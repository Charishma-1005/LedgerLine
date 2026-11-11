package com.ledgerline.identity.application;

import com.ledgerline.account.application.AuthenticatedUserIdProvider;
import com.ledgerline.audit.application.AuditService;
import com.ledgerline.audit.domain.AuditEvent;
import com.ledgerline.identity.domain.IdentityUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Locale;

@Service
public class AdminManagementService {
    private final IdentityUserStore users;
    private final AuthenticatedUserIdProvider authenticatedUser;
    private final AuditService audit;
    private final SessionRegistry sessions;
    private final AdminDashboardStore dashboard;

    public AdminManagementService(IdentityUserStore users, AuthenticatedUserIdProvider authenticatedUser,
                                  AuditService audit, SessionRegistry sessions, AdminDashboardStore dashboard) {
        this.users = users;
        this.authenticatedUser = authenticatedUser;
        this.audit = audit;
        this.sessions = sessions;
        this.dashboard = dashboard;
    }

    @Transactional(readOnly = true)
    public List<IdentityUser> listUsers() {
        requireAdminId();
        return users.findAllUsers();
    }

    @Transactional(readOnly = true)
    public IdentityUserPage searchUsers(String emailQuery, int page, int size) {
        requireAdminId();
        String normalizedQuery = emailQuery == null ? "" : emailQuery.strip().toLowerCase(Locale.ROOT);
        if (normalizedQuery.length() > 254) normalizedQuery = normalizedQuery.substring(0, 254);
        return users.searchUsers(normalizedQuery, Math.max(0, Math.min(page, 100_000)),
                Math.max(1, Math.min(size, 50)));
    }

    @Transactional(readOnly = true)
    public AdminDashboardStats dashboardStats() {
        requireAdminId();
        return dashboard.loadStatistics();
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> recentEvents() {
        requireAdminId();
        return audit.recentEvents();
    }

    @Transactional
    public void setUserEnabled(long targetUserId, boolean enabled) {
        long actorId = requireAdminId();
        List<IdentityUser> locked = users.lockAllUsers();
        IdentityUser actor = locked.stream().filter(user -> user.id() == actorId).findFirst()
                .orElseThrow(() -> new AccessDeniedException("Administrator account is unavailable"));
        if (!actor.enabled() || actor.role() != com.ledgerline.identity.domain.UserRole.ADMIN) {
            throw new AccessDeniedException("Administrator account is disabled or no longer has the required role");
        }
        IdentityUser target = locked.stream().filter(user -> user.id() == targetUserId).findFirst()
                .orElseThrow(() -> new AdminManagementException("User was not found"));
        if (target.enabled() == enabled) return;
        if (!enabled && target.id() == actorId) {
            throw new AdminManagementException("Administrators cannot disable their own account");
        }
        if (!enabled && target.role() == com.ledgerline.identity.domain.UserRole.ADMIN
                && users.countEnabledAdmins() <= 1) {
            throw new AdminManagementException("The last enabled administrator cannot be disabled");
        }
        if (users.setEnabled(targetUserId, enabled) != 1) {
            throw new AdminManagementException("User status could not be changed");
        }
        if (!enabled) expireSessions(target.normalizedEmail());
        String action = enabled ? "ADMIN_USER_ENABLED" : "ADMIN_USER_DISABLED";
        audit.recordAuthenticated(action, "USER", targetUserId,
                Map.of("enabled", Boolean.toString(enabled)));
    }

    private long requireAdminId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getAuthorities().stream().noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) {
            throw new AccessDeniedException("Administrator role is required");
        }
        long actorId = authenticatedUser.requireUserId();
        IdentityUser actor = users.findById(actorId)
                .orElseThrow(() -> new AccessDeniedException("Administrator account is unavailable"));
        if (!actor.enabled() || actor.role() != com.ledgerline.identity.domain.UserRole.ADMIN) {
            throw new AccessDeniedException("Administrator account is disabled or no longer has the required role");
        }
        return actorId;
    }

    private void expireSessions(String normalizedEmail) {
        for (Object principal : sessions.getAllPrincipals()) {
            if (principal instanceof UserDetails details && normalizedEmail.equals(details.getUsername())) {
                sessions.getAllSessions(principal, false).forEach(session -> session.expireNow());
            }
        }
    }
}
