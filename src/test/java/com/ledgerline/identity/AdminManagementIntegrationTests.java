package com.ledgerline.identity;

import com.ledgerline.account.application.FinancialAccountService;
import com.ledgerline.account.domain.FinancialAccount;
import com.ledgerline.account.domain.FinancialAccountType;
import com.ledgerline.audit.application.AuditService;
import com.ledgerline.identity.application.AdminBootstrapService;
import com.ledgerline.identity.application.AdminManagementException;
import com.ledgerline.identity.application.AdminManagementService;
import com.ledgerline.identity.application.AdminDashboardStats;
import com.ledgerline.identity.application.IdentityUserStore;
import com.ledgerline.identity.application.RegistrationService;
import com.ledgerline.identity.domain.UserRole;
import com.ledgerline.identity.infrastructure.DatabaseUserDetailsService;
import com.ledgerline.ledger.application.FinancialOperationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminManagementIntegrationTests {
    private static final String PASSWORD = "A-Longer-Password-928!";
    private String adminEmail;
    private String ordinaryEmail;
    private long adminId;
    private long ordinaryId;

    @Autowired private RegistrationService registration;
    @Autowired private IdentityUserStore users;
    @Autowired private AdminBootstrapService bootstrap;
    @Autowired private AdminManagementService admins;
    @Autowired private FinancialAccountService accounts;
    @Autowired private FinancialOperationService operations;
    @Autowired private DatabaseUserDetailsService userDetails;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mockMvc;

    @BeforeEach
    void createUsersAndBootstrapOneAdmin() {
        String suffix = UUID.randomUUID().toString();
        String bootstrapCandidateEmail = "bootstrap-" + suffix + "@example.com";
        ordinaryEmail = "ordinary-" + suffix + "@example.com";
        registration.register(bootstrapCandidateEmail, PASSWORD);
        registration.register(ordinaryEmail, PASSWORD);
        ordinaryId = users.findByNormalizedEmail(ordinaryEmail).orElseThrow().id();
        var existingAdmin = users.findAllUsers().stream().filter(user -> user.role() == UserRole.ADMIN).findFirst();
        if (existingAdmin.isPresent()) {
            adminEmail = existingAdmin.get().normalizedEmail();
            adminId = existingAdmin.get().id();
        } else {
            adminEmail = bootstrapCandidateEmail;
            assertTrue(bootstrap.promoteConfiguredUser(adminEmail));
            adminId = users.findByNormalizedEmail(adminEmail).orElseThrow().id();
        }
    }

    @Test
    void bootstrapPromotesOnlyAnExistingEnabledUserAndNeverCreatesAnAdminFromPublicRegistration() throws Exception {
        assertEquals(UserRole.ADMIN, users.findById(adminId).orElseThrow().role());
        assertFalse(bootstrap.promoteConfiguredUser(ordinaryEmail));

        String publicEmail = "public-role-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/register").with(csrf())
                        .param("email", publicEmail)
                        .param("password", PASSWORD)
                        .param("role", "ADMIN"))
                .andExpect(status().is3xxRedirection());
        assertEquals(UserRole.USER, users.findByNormalizedEmail(publicEmail).orElseThrow().role());
        assertEquals(1, users.countEnabledAdmins());
    }

    @Test
    void adminRoutesRejectOrdinaryAndAnonymousUsersAndAllowBootstrappedAdmin() throws Exception {
        mockMvc.perform(get("/admin")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/admin").with(user(ordinaryEmail).roles("USER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/users").with(user(adminEmail).roles("ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/admin/users/{id}/enabled", ordinaryId)
                        .with(user(ordinaryEmail).roles("USER")).with(csrf()).param("enabled", "false"))
                .andExpect(status().isForbidden());
        assertTrue(userDetails.loadUserByUsername(adminEmail).getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority())));
        assertTrue(userDetails.loadUserByUsername(adminEmail).getAuthorities().stream()
                .anyMatch(authority -> "ROLE_USER".equals(authority.getAuthority())));
    }

    @Test
    void adminCanDisableAndEnableUsersWithAuditAndLoginStatusUpdated() throws Exception {
        mockMvc.perform(post("/admin/users/{id}/enabled", ordinaryId)
                        .with(user(adminEmail).roles("ADMIN"))
                        .with(csrf())
                        .param("enabled", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("adminFeedback", "User access has been disabled."));
        assertFalse(users.findById(ordinaryId).orElseThrow().enabled());
        assertFalse(userDetails.loadUserByUsername(ordinaryEmail).isEnabled());

        mockMvc.perform(post("/admin/users/{id}/enabled", ordinaryId)
                        .with(user(adminEmail).roles("ADMIN"))
                        .with(csrf())
                        .param("enabled", "true"))
                .andExpect(status().is3xxRedirection());
        assertTrue(users.findById(ordinaryId).orElseThrow().enabled());
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_event where actor_owner_id = ? and entity_id = ? and event_type = 'ADMIN_USER_DISABLED'", Integer.class, adminId, ordinaryId));
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_event where actor_owner_id = ? and entity_id = ? and event_type = 'ADMIN_USER_ENABLED'", Integer.class, adminId, ordinaryId));
    }

    @Test
    void adminCannotDisableSelfOrTheLastEnabledAdmin() {
        assertThrows(AdminManagementException.class, () -> asAdmin(() -> {
            admins.setUserEnabled(adminId, false);
            return null;
        }));
        assertTrue(users.findById(adminId).orElseThrow().enabled());
    }

    @Test
    void disabledUserCannotUseAnAlreadyAuthenticatedPrincipalForServiceAccess() {
        asAdmin(() -> {
            admins.setUserEnabled(ordinaryId, false);
            return null;
        });
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(ordinaryEmail, "", List.of(() -> "ROLE_USER")));
        try {
            assertThrows(AccessDeniedException.class, accounts::listMine);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void selfDisableRequestReturnsConflictWithoutDisablingAdmin() throws Exception {
        mockMvc.perform(post("/admin/users/{id}/enabled", adminId)
                        .with(user(adminEmail).roles("ADMIN"))
                        .with(csrf())
                        .param("enabled", "false"))
                .andExpect(status().isConflict())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Administrators cannot disable their own account")));
        assertTrue(users.findById(adminId).orElseThrow().enabled());
    }

    @Test
    void adminOverviewDisplaysLiveUserAndAccountStatisticsAndRecentAuditData() throws Exception {
        long totalUsers = jdbc.queryForObject("select count(*) from app_user", Long.class);
        long enabledUsers = jdbc.queryForObject("select count(*) from app_user where enabled = true", Long.class);
        long disabledUsers = jdbc.queryForObject("select count(*) from app_user where enabled = false", Long.class);
        long totalAccounts = jdbc.queryForObject("select count(*) from financial_account", Long.class);
        long activeAccounts = jdbc.queryForObject("select count(*) from financial_account where active = true", Long.class);
        long closedAccounts = jdbc.queryForObject("select count(*) from financial_account where active = false", Long.class);
        asAdmin(() -> {
            admins.setUserEnabled(ordinaryId, false);
            return null;
        });

        mockMvc.perform(get("/admin").with(user(adminEmail).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("stats", new AdminDashboardStats(totalUsers, enabledUsers - 1,
                        disabledUsers + 1, totalAccounts, activeAccounts, closedAccounts)))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Recent audit activity")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ADMIN_USER_DISABLED")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("User #")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("password_hash"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("metadata"))));
    }

    @Test
    void adminUserSearchIsCaseInsensitiveAndPagedWithOnlySafeIdentityFields() throws Exception {
        String emailPrefix = "admin-page-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        for (int index = 0; index < 26; index++) registration.register(emailPrefix + "-" + index + "@example.com", PASSWORD);
        String firstEmail = emailPrefix + "-0@example.com";
        String firstHash = users.findByNormalizedEmail(firstEmail).orElseThrow().passwordHash();

        mockMvc.perform(get("/admin/users").param("q", emailPrefix.toUpperCase(java.util.Locale.ROOT))
                        .with(user(adminEmail).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("totalUsers", 26L))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Page 1 of 2")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(firstEmail)))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(firstHash))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-dialog-open=\"user-action-")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("_csrf")));

        mockMvc.perform(get("/admin/users").param("q", emailPrefix).param("page", "1")
                        .with(user(adminEmail).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Page 2 of 2")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(firstEmail))));
    }

    @Test
    void registrationAccountAndFinancialOperationsWriteActorScopedAuditEvents() {
        FinancialAccount first = asAdmin(() -> accounts.create("Primary", FinancialAccountType.BANK, "USD"));
        FinancialAccount second = asAdmin(() -> accounts.create("Savings", FinancialAccountType.WALLET, "USD"));
        asAdmin(() -> operations.deposit(first.id(), new BigDecimal("50.00"), "USD", "audit-deposit"));
        asAdmin(() -> operations.withdraw(first.id(), new BigDecimal("5.00"), "USD", "audit-withdraw"));
        asAdmin(() -> operations.transfer(first.id(), second.id(), new BigDecimal("10.00"), "USD", "audit-transfer"));
        asAdmin(() -> accounts.closeMine(second.id()));

        assertEquals(1, jdbc.queryForObject("select count(*) from audit_event where event_type = 'USER_REGISTERED' and actor_owner_id is null and entity_id = ?", Integer.class, adminId));
        for (String action : List.of("ACCOUNT_CREATED", "ACCOUNT_CLOSED", "DEPOSIT", "WITHDRAWAL", "TRANSFER")) {
            assertTrue(jdbc.queryForObject("select count(*) from audit_event where event_type = ? and actor_owner_id = ?", Integer.class, action, adminId) > 0, action);
        }
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_event where event_type = 'ADMIN_BOOTSTRAPPED' and actor_owner_id is null and entity_id = ?", Integer.class, adminId));
        assertFalse(jdbc.queryForObject("select details from audit_event where event_type = 'DEPOSIT' and actor_owner_id = ?", String.class, adminId).contains("password"));
    }

    @Test
    void auditEventsCannotBeUpdatedOrDeleted() {
        Long eventId = jdbc.queryForObject("select id from audit_event where event_type = 'USER_REGISTERED' and entity_id = ?", Long.class, ordinaryId);
        assertThrows(DataAccessException.class, () -> jdbc.update("update audit_event set details = 'tampered' where id = ?", eventId));
        assertThrows(DataAccessException.class, () -> jdbc.update("delete from audit_event where id = ?", eventId));
    }

    private <T> T asAdmin(Callable<T> work) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(adminEmail, "", List.of(() -> "ROLE_USER", () -> "ROLE_ADMIN")));
        try { return work.call(); }
        catch (RuntimeException exception) { throw exception; }
        catch (Exception exception) { throw new RuntimeException(exception); }
        finally { SecurityContextHolder.clearContext(); }
    }
}
