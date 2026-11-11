package com.ledgerline.dashboard;

import com.ledgerline.account.application.FinancialAccountService;
import com.ledgerline.account.domain.FinancialAccountType;
import com.ledgerline.identity.application.RegistrationService;
import com.ledgerline.ledger.application.FinancialOperationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardIntegrationTests {
    private static final String PASSWORD = "Dashboard-Test-Password-928!";

    @Autowired private MockMvc mockMvc;
    @Autowired private RegistrationService registration;
    @Autowired private FinancialAccountService accounts;
    @Autowired private FinancialOperationService operations;

    @Test
    void authenticatedDashboardShowsOwnerLedgerDataAndRecentPostedActivityOnly() throws Exception {
        String ownerEmail = registerUnique("dashboard-owner");
        String otherEmail = registerUnique("dashboard-other");
        var ownerAccount = asUser(ownerEmail, () -> accounts.create("Daily account", FinancialAccountType.BANK, "USD"));
        var otherAccount = asUser(otherEmail, () -> accounts.create("Other private account", FinancialAccountType.CASH, "USD"));
        asUser(ownerEmail, () -> operations.deposit(ownerAccount.id(), new BigDecimal("125.50"), "USD", "dash-owner-deposit"));
        asUser(otherEmail, () -> operations.deposit(otherAccount.id(), new BigDecimal("9999.00"), "USD", "dash-other-deposit"));

        mockMvc.perform(get("/").with(user(ownerEmail).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Welcome back")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Daily account")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("125.50")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("DEPOSIT")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Posted")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/transactions/new?operation=DEPOSIT")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/transactions/new?operation=WITHDRAWAL")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/transactions/new?operation=TRANSFER")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Other private account"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("9999.00"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf\"")));
    }

    @Test
    void dashboardShowsHonestEmptyStatesAndAnonymousVisitorGetsLandingPage() throws Exception {
        String email = registerUnique("dashboard-empty");
        mockMvc.perform(get("/").with(user(email).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("No accounts yet")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("No transactions yet")));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Money, in")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Get started")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("No accounts yet"))));
    }

    @Test
    void quickActionDeepLinkPreselectsTheRequestedExistingOperation() throws Exception {
        String email = registerUnique("dashboard-action");
        mockMvc.perform(get("/transactions/new").param("operation", "TRANSFER").with(user(email).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<option value=\"TRANSFER\" selected=\"selected\">Transfer</option>")));
    }

    private String registerUnique(String prefix) {
        String email = prefix + "-" + UUID.randomUUID() + "@example.com";
        registration.register(email, PASSWORD);
        return email;
    }

    private <T> T asUser(String email, Callable<T> action) {
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
