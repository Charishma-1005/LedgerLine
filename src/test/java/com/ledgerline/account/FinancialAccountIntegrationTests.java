package com.ledgerline.account;

import com.ledgerline.account.application.FinancialAccountStore;
import com.ledgerline.account.domain.FinancialAccount;
import com.ledgerline.identity.application.IdentityUserStore;
import com.ledgerline.identity.application.RegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
class FinancialAccountIntegrationTests {
    private static final String PASSWORD = "A-Longer-Password-928!";
    private String ownerEmail;
    private String otherEmail;
    private long ownerId;
    private long otherId;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private IdentityUserStore users;

    @Autowired
    private FinancialAccountStore accounts;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void createUsers() {
        String suffix = UUID.randomUUID().toString();
        ownerEmail = "account-owner-" + suffix + "@example.com";
        otherEmail = "account-other-" + suffix + "@example.com";
        registrationService.register(ownerEmail, PASSWORD);
        registrationService.register(otherEmail, PASSWORD);
        ownerId = users.findByNormalizedEmail(ownerEmail).orElseThrow().id();
        otherId = users.findByNormalizedEmail(otherEmail).orElseThrow().id();
    }

    @Test
    void createsSupportedAccountTypesAndTheirOwnedAssetLedgerAccounts() throws Exception {
        for (String type : List.of("BANK", "WALLET", "CASH")) {
            createAccount(ownerEmail, type + " account", type, "USD", otherId);
        }

        List<FinancialAccount> created = accounts.findAllForOwner(ownerId);
        assertEquals(3, created.size());
        assertTrue(created.stream().allMatch(account -> account.ledgerAccountId() != null));
        assertEquals(3, jdbc.queryForObject("""
                select count(*)
                from financial_account f
                join ledger_account l on l.id = f.ledger_account_id
                where f.owner_id = ? and l.owner_id = f.owner_id
                  and l.currency_code = f.currency_code and l.account_type = 'ASSET'
                  and l.is_system = false
                """, Integer.class, ownerId));
        assertEquals(0, accounts.findAllForOwner(otherId).size());
    }

    @Test
    void listsOnlyCurrentUsersAccounts() throws Exception {
        createAccount(ownerEmail, "My checking", "BANK", "USD", null);
        long otherAccountId = createAccount(otherEmail, "Private wallet", "WALLET", "EUR", null);

        mockMvc.perform(get("/accounts").with(user(ownerEmail).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("My checking")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Private wallet"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("/accounts/" + otherAccountId))));
    }

    @Test
    void accountPageShowsLedgerDerivedBalanceWithoutExposingLedgerId() throws Exception {
        long accountId = createAccount(ownerEmail, "Ledger-backed wallet", "WALLET", "USD", null);
        mockMvc.perform(post("/transactions/deposit")
                        .with(user(ownerEmail).roles("USER")).with(csrf())
                        .param("accountId", Long.toString(accountId))
                        .param("amount", "42.50")
                        .param("currencyCode", "USD")
                        .param("idempotencyKey", "account-page-balance-" + UUID.randomUUID()))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/accounts").with(user(ownerEmail).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ledger-backed wallet")))
                .andExpect(content().string(containsString("42.5000")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("ledgerAccountId"))));
    }

    @Test
    void accountCreationPageHasValidatedFieldsAndDoesNotAcceptOwnerOrLedgerIds() throws Exception {
        mockMvc.perform(get("/accounts/new").with(user(ownerEmail).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("Currency")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("ownerId"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("ledgerAccountId"))));

        mockMvc.perform(post("/accounts")
                        .with(user(ownerEmail).roles("USER")).with(csrf())
                        .param("displayName", " ")
                        .param("type", "BANK")
                        .param("currencyCode", "usd"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Add an account")))
                .andExpect(content().string(containsString("Enter a three-letter uppercase ISO currency code")));
        assertEquals(0, accounts.findAllForOwner(ownerId).size());
    }

    @Test
    void successfulAccountCreationShowsFeedback() throws Exception {
        MvcResult created = mockMvc.perform(post("/accounts")
                        .with(user(ownerEmail).roles("USER")).with(csrf())
                        .param("displayName", "Rainy day wallet")
                        .param("type", "WALLET")
                        .param("currencyCode", "USD"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        mockMvc.perform(get(created.getResponse().getRedirectedUrl())
                        .with(user(ownerEmail).roles("USER")).flashAttrs(created.getFlashMap()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Account created successfully.")));
    }

    @Test
    void ownerCanViewAccountDetails() throws Exception {
        long accountId = createAccount(ownerEmail, "Travel cash", "CASH", "INR", null);

        mockMvc.perform(get("/accounts/{id}", accountId).with(user(ownerEmail).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Travel cash")))
                .andExpect(content().string(containsString("INR")))
                .andExpect(content().string(containsString("Active")))
                .andExpect(content().string(containsString("aria-labelledby=\"close-dialog-title\"")))
                .andExpect(content().string(containsString("Close this account?")));
    }

    @Test
    void accountsPageOffersHelpfulEmptyState() throws Exception {
        mockMvc.perform(get("/accounts").with(user(ownerEmail).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Your accounts start here")))
                .andExpect(content().string(containsString("Create an account")));
    }

    @Test
    void displayNameUpdateDoesNotChangeAccountTypeCurrencyOrLedgerLink() throws Exception {
        long accountId = createAccount(ownerEmail, "Old name", "BANK", "USD", null);
        FinancialAccount before = accounts.findByIdForOwner(accountId, ownerId).orElseThrow();

        MvcResult renamed = mockMvc.perform(post("/accounts/{id}/name", accountId)
                        .with(user(ownerEmail).roles("USER"))
                        .with(csrf())
                        .param("displayName", "  Main checking  "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/accounts/" + accountId))
                .andReturn();
        mockMvc.perform(get("/accounts/{id}", accountId)
                        .with(user(ownerEmail).roles("USER")).flashAttrs(renamed.getFlashMap()))
                .andExpect(content().string(containsString("Display name updated.")));

        FinancialAccount after = accounts.findByIdForOwner(accountId, ownerId).orElseThrow();
        assertEquals("Main checking", after.displayName());
        assertEquals(before.type(), after.type());
        assertEquals(before.currencyCode(), after.currencyCode());
        assertEquals(before.ledgerAccountId(), after.ledgerAccountId());
        assertEquals("Main checking", jdbc.queryForObject(
                "select name from ledger_account where id = ? and owner_id = ?",
                String.class, after.ledgerAccountId(), ownerId));
    }

    @Test
    void closeDeactivatesWithoutDeletingAndCanBeRepeatedSafely() throws Exception {
        long accountId = createAccount(ownerEmail, "Old wallet", "WALLET", "USD", null);

        MvcResult closeResult = mockMvc.perform(post("/accounts/{id}/close", accountId)
                        .with(user(ownerEmail).roles("USER"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        mockMvc.perform(get("/accounts/{id}", accountId)
                        .with(user(ownerEmail).roles("USER")).flashAttrs(closeResult.getFlashMap()))
                .andExpect(content().string(containsString("Account closed. Its history has been kept.")));

        FinancialAccount closed = accounts.findByIdForOwner(accountId, ownerId).orElseThrow();
        assertFalse(closed.active());
        assertNotNull(closed.ledgerAccountId());

        mockMvc.perform(get("/accounts/{id}", accountId).with(user(ownerEmail).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Closed")));

        mockMvc.perform(post("/accounts/{id}/close", accountId)
                        .with(user(ownerEmail).roles("USER"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertFalse(accounts.findByIdForOwner(accountId, ownerId).orElseThrow().active());
    }

    @Test
    void anotherUserCannotViewRenameOrCloseAccountById() throws Exception {
        long accountId = createAccount(otherEmail, "Other user's account", "BANK", "GBP", null);

        mockMvc.perform(get("/accounts/{id}", accountId).with(user(ownerEmail).roles("USER")))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/accounts/{id}/name", accountId)
                        .with(user(ownerEmail).roles("USER"))
                        .with(csrf())
                        .param("displayName", "Hijacked"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/accounts/{id}/close", accountId)
                        .with(user(ownerEmail).roles("USER"))
                        .with(csrf()))
                .andExpect(status().isNotFound());

        FinancialAccount stillOwnedByOther = accounts.findByIdForOwner(accountId, otherId).orElseThrow();
        assertEquals("Other user's account", stillOwnedByOther.displayName());
        assertTrue(stillOwnedByOther.active());
        assertNotEquals(ownerId, stillOwnedByOther.ownerId());
    }

    private long createAccount(String email, String displayName, String type, String currency,
                               Long forgedOwnerId) throws Exception {
        var request = post("/accounts")
                .with(user(email).roles("USER"))
                .with(csrf())
                .param("displayName", displayName)
                .param("type", type)
                .param("currencyCode", currency);
        if (forgedOwnerId != null) {
            request.param("ownerId", forgedOwnerId.toString());
        }
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String redirect = result.getResponse().getRedirectedUrl();
        return Long.parseLong(redirect.substring(redirect.lastIndexOf('/') + 1));
    }
}
