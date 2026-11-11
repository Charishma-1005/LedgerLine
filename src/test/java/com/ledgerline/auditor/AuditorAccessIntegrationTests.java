package com.ledgerline.auditor;

import com.ledgerline.account.application.FinancialAccountStore;
import com.ledgerline.account.domain.FinancialAccountType;
import com.ledgerline.identity.application.AdminBootstrapService;
import com.ledgerline.identity.application.AuditorBootstrapService;
import com.ledgerline.identity.application.IdentityUserStore;
import com.ledgerline.identity.application.RegistrationService;
import com.ledgerline.identity.domain.IdentityUser;
import com.ledgerline.identity.domain.UserRole;
import com.ledgerline.ledger.application.FinancialOperationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditorAccessIntegrationTests {
    private static final String PASSWORD = "Auditor-Integration-Password-927!";

    private String auditorEmail;
    private String adminEmail;
    private String customerEmail;
    private IdentityUser customer;
    private long customerAccountId;

    @Autowired private RegistrationService registration;
    @Autowired private AuditorBootstrapService auditorBootstrap;
    @Autowired private AdminBootstrapService adminBootstrap;
    @Autowired private IdentityUserStore users;
    @Autowired private FinancialAccountStore accounts;
    @Autowired private FinancialOperationService operations;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        customerEmail = "auditor-customer-" + suffix + "@example.com";
        customer = registration.register(customerEmail, PASSWORD);
        var account = accounts.createForOwner(customer.id(), "Customer bank", FinancialAccountType.BANK,
                "USD", "AUD-FA-" + suffix);
        customerAccountId = account.id();
        as(customerEmail, () -> operations.deposit(account.id(), new BigDecimal("42.50"), "USD", "aud-" + suffix));
        auditorEmail = ensureRole(UserRole.AUDITOR, "auditor-operator-" + suffix + "@example.com");
        adminEmail = ensureRole(UserRole.ADMIN, "admin-operator-" + suffix + "@example.com");
    }

    @Test
    void bootstrappedAuditorCanReadRecentAuditEventsAndPostedStatementLines() throws Exception {
        assertTrue(users.findByNormalizedEmail(auditorEmail).orElseThrow().role() == UserRole.AUDITOR);
        mockMvc.perform(post("/login").with(csrf()).param("email", auditorEmail).param("password", PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auditor"));
        mockMvc.perform(get("/auditor").with(user(auditorEmail).roles("AUDITOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Audit events")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Transaction statement")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(customerEmail)))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("42.50")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("AUDITOR_BOOTSTRAPPED")));
    }

    @Test
    void userIsDeniedAndAdminRetainsAdminAccessButCannotEnterAuditorOnlyInterface() throws Exception {
        String userEmail = customerEmail;
        mockMvc.perform(get("/auditor").with(user(userEmail).roles("USER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/auditor").with(user(adminEmail).roles("USER", "ADMIN")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin").with(user(adminEmail).roles("USER", "ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void auditorCannotCreateAccountsMoveMoneyModifyUsersOrPostAuditChanges() throws Exception {
        int accountsBefore = jdbc.queryForObject("select count(*) from financial_account", Integer.class);
        int transactionsBefore = jdbc.queryForObject("select count(*) from journal_transaction", Integer.class);
        int usersBefore = jdbc.queryForObject("select count(*) from app_user", Integer.class);

        mockMvc.perform(post("/accounts").with(user(auditorEmail).roles("AUDITOR")).with(csrf())
                        .param("displayName", "Unauthorized")
                        .param("type", "BANK").param("currencyCode", "USD"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/transactions/deposit").with(user(auditorEmail).roles("AUDITOR")).with(csrf())
                        .param("accountId", Long.toString(customerAccountId))
                        .param("amount", "1.00").param("currencyCode", "USD")
                        .param("idempotencyKey", "auditor-write-denial"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/users/{id}/enabled", customer.id())
                        .with(user(auditorEmail).roles("AUDITOR")).with(csrf()).param("enabled", "false"))
                .andExpect(status().isForbidden());
        // Auditor routes expose GET only; no POST handler exists for audit or statement data.
        mockMvc.perform(post("/auditor").with(user(auditorEmail).roles("AUDITOR")).with(csrf()))
                .andExpect(status().isMethodNotAllowed());

        assertEquals(accountsBefore, jdbc.queryForObject("select count(*) from financial_account", Integer.class));
        assertEquals(transactionsBefore, jdbc.queryForObject("select count(*) from journal_transaction", Integer.class));
        assertEquals(usersBefore, jdbc.queryForObject("select count(*) from app_user", Integer.class));
    }

    @Test
    void publicRegistrationCannotAssignAuditorRole() throws Exception {
        String email = "no-public-auditor-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/register").with(csrf()).param("email", email).param("password", PASSWORD)
                        .param("role", "AUDITOR"))
                .andExpect(status().is3xxRedirection());
        assertEquals(UserRole.USER, users.findByNormalizedEmail(email).orElseThrow().role());
    }

    private String ensureRole(UserRole role, String candidateEmail) {
        var existing = users.findAllUsers().stream().filter(user -> user.role() == role).findFirst();
        if (existing.isPresent()) return existing.get().normalizedEmail();
        registration.register(candidateEmail, PASSWORD);
        boolean promoted = role == UserRole.AUDITOR
                ? auditorBootstrap.promoteConfiguredAuditor(candidateEmail)
                : adminBootstrap.promoteConfiguredUser(candidateEmail);
        assertTrue(promoted, "controlled bootstrap should assign the first " + role);
        return users.findByNormalizedEmail(candidateEmail).orElseThrow().normalizedEmail();
    }

    private <T> T as(String email, Callable<T> action) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                email, "", List.of(() -> "ROLE_USER")));
        try {
            return action.call();
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
