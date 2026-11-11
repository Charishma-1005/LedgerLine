package com.ledgerline.reporting;

import com.ledgerline.account.application.FinancialAccountStore;
import com.ledgerline.account.domain.FinancialAccount;
import com.ledgerline.account.domain.FinancialAccountType;
import com.ledgerline.identity.application.IdentityUserStore;
import com.ledgerline.identity.application.RegistrationService;
import com.ledgerline.ledger.application.FinancialOperationService;
import com.ledgerline.reporting.application.ReportsData;
import com.ledgerline.reporting.application.ReportsQueryService;
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
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportsIntegrationTests {
    private static final String PASSWORD = "A-Longer-Password-928!";
    private String email;
    private String otherEmail;
    private long ownerId;
    private FinancialAccount checking;
    private FinancialAccount savings;
    private FinancialAccount euro;
    private FinancialAccount otherAccount;

    @Autowired private RegistrationService registration;
    @Autowired private IdentityUserStore users;
    @Autowired private FinancialAccountStore accounts;
    @Autowired private FinancialOperationService operations;
    @Autowired private ReportsQueryService reports;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        String suffix = UUID.randomUUID().toString();
        email = "reports-" + suffix + "@example.com";
        otherEmail = "other-reports-" + suffix + "@example.com";
        registration.register(email, PASSWORD);
        registration.register(otherEmail, PASSWORD);
        ownerId = users.findByNormalizedEmail(email).orElseThrow().id();
        long otherId = users.findByNormalizedEmail(otherEmail).orElseThrow().id();
        checking = create(ownerId, "Main checking", "USD");
        savings = create(ownerId, "Savings", "USD");
        euro = create(ownerId, "Euro wallet", "EUR");
        otherAccount = create(otherId, "Other private account", "USD");
    }

    @Test
    void reportsKeepCurrenciesSeparateAndShowTransfersOutsideIncomeAndExpenses() throws Exception {
        as(email, () -> operations.deposit(checking.id(), new BigDecimal("100.00"), "USD", "report-usd-deposit"));
        as(email, () -> operations.withdraw(checking.id(), new BigDecimal("20.00"), "USD", "report-usd-withdraw"));
        as(email, () -> operations.transfer(checking.id(), savings.id(), new BigDecimal("30.00"), "USD", "report-usd-transfer"));
        as(email, () -> operations.deposit(euro.id(), new BigDecimal("40.00"), "EUR", "report-eur-deposit"));
        as(otherEmail, () -> operations.deposit(otherAccount.id(), new BigDecimal("500.00"), "USD", "other-report-deposit"));

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        ReportsData data = as(email, () -> reports.load(today, today));
        assertEquals(2, data.currencies().size());
        ReportsData.CurrencySummary usd = data.currencies().stream().filter(item -> item.currencyCode().equals("USD")).findFirst().orElseThrow();
        ReportsData.CurrencySummary eur = data.currencies().stream().filter(item -> item.currencyCode().equals("EUR")).findFirst().orElseThrow();
        assertMoney("100.00", usd.deposits());
        assertMoney("20.00", usd.withdrawals());
        assertMoney("30.00", usd.transfers());
        assertMoney("0", usd.income());
        assertMoney("0", usd.expenses());
        assertEquals(3, usd.transactionCount());
        assertMoney("40.00", eur.deposits());
        assertMoney("0", eur.income());

        assertMoney("50.00", data.accounts().stream().filter(item -> item.displayName().equals("Main checking")).findFirst().orElseThrow().balance());
        assertMoney("30.00", data.accounts().stream().filter(item -> item.displayName().equals("Savings")).findFirst().orElseThrow().balance());
        assertMoney("40.00", data.accounts().stream().filter(item -> item.displayName().equals("Euro wallet")).findFirst().orElseThrow().balance());

        mockMvc.perform(get("/reports").param("start", today.toString()).param("end", today.toString())
                        .with(user(email).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Income, expenses &amp; money movement")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("EUR")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("USD")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("500.00"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("aria-label=\"Monthly deposit, withdrawal and transfer amounts in USD\"")));
    }

    @Test
    void incomeAndExpensesComeFromLedgerAccountsAndReversedPairsAreExcluded() {
        long expenseLedger = createLedgerAccount("RPT-EXP-" + UUID.randomUUID(), "EXPENSE");
        long incomeLedger = createLedgerAccount("RPT-INC-" + UUID.randomUUID(), "INCOME");
        long incomeTransaction = createDraft("Income fixture", null);
        entry(incomeTransaction, checking.ledgerAccountId(), "USD", "50.00", "0.00");
        entry(incomeTransaction, incomeLedger, "USD", "0.00", "50.00");
        post(incomeTransaction);
        long expenseTransaction = createDraft("Expense fixture", null);
        entry(expenseTransaction, expenseLedger, "USD", "12.00", "0.00");
        entry(expenseTransaction, checking.ledgerAccountId(), "USD", "0.00", "12.00");
        post(expenseTransaction);

        long reversedOriginal = createDraft("Reversed expense fixture", null);
        entry(reversedOriginal, expenseLedger, "USD", "90.00", "0.00");
        entry(reversedOriginal, checking.ledgerAccountId(), "USD", "0.00", "90.00");
        markReversed(reversedOriginal);
        long reversal = createDraft("Reversal fixture", reversedOriginal);
        entry(reversal, checking.ledgerAccountId(), "USD", "90.00", "0.00");
        entry(reversal, expenseLedger, "USD", "0.00", "90.00");
        post(reversal);

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        ReportsData.CurrencySummary usd = as(email, () -> reports.load(today, today)).currencies().stream()
                .filter(item -> item.currencyCode().equals("USD")).findFirst().orElseThrow();
        assertMoney("50.00", usd.income());
        assertMoney("12.00", usd.expenses());
        assertEquals(0, usd.transactionCount());
        assertTrue(as(email, () -> reports.load(today, today)).monthlyTrends().isEmpty());
    }

    @Test
    void legacyTransactionsRemainAllowedByTheMigrationConstraint() {
        long transactionId = createDraft("Legacy transaction fixture", null);

        assertEquals("LEGACY", jdbc.queryForObject(
                "select transaction_type from journal_transaction where id = ?", String.class, transactionId));
    }

    @Test
    void dateFilterChangesActivityPeriodButCurrentAccountBalancesRemainAvailable() {
        as(email, () -> operations.deposit(checking.id(), new BigDecimal("25.00"), "USD", "report-date-filter"));
        LocalDate tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1);
        ReportsData data = as(email, () -> reports.load(tomorrow, tomorrow));
        assertTrue(data.currencies().isEmpty());
        assertTrue(data.monthlyTrends().isEmpty());
        assertEquals(3, data.accounts().size());
        assertMoney("25.00", data.accounts().stream().filter(item -> item.displayName().equals("Main checking"))
                .findFirst().orElseThrow().balance());
    }

    @Test
    void reportsAreRestrictedToAuthenticatedUsersAndTheUserRole() throws Exception {
        mockMvc.perform(get("/reports")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/reports").with(user(email).roles("GUEST"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/reports").with(user(email).roles("USER"))).andExpect(status().isOk());
    }

    private FinancialAccount create(long owner, String name, String currency) {
        return accounts.createForOwner(owner, name, FinancialAccountType.BANK, currency,
                "RPT-FA-" + UUID.randomUUID());
    }

    private long createLedgerAccount(String code, String type) {
        Instant now = Instant.now();
        jdbc.update("""
                insert into ledger_account(owner_id, code, name, account_type, currency_code, is_system, created_at)
                values (?, ?, ?, ?, 'USD', false, ?)
                """, ownerId, code, code, type, Timestamp.from(now));
        return jdbc.queryForObject("select id from ledger_account where owner_id = ? and code = ?", Long.class, ownerId, code);
    }

    private long createDraft(String description, Long reversalOf) {
        Instant now = Instant.now();
        jdbc.update("""
                insert into journal_transaction(owner_id, reversal_of_transaction_id, description, status,
                                                occurred_at, posted_at, created_at, transaction_type)
                values (?, ?, ?, 'DRAFT', ?, null, ?, 'LEGACY')
                """, ownerId, reversalOf, description, Timestamp.from(now), Timestamp.from(now));
        return jdbc.queryForObject("select id from journal_transaction where owner_id = ? and description = ?",
                Long.class, ownerId, description);
    }

    private void entry(long transactionId, long ledgerId, String currency, String debit, String credit) {
        jdbc.update("""
                insert into journal_entry(journal_transaction_id, ledger_account_id, currency_code,
                                          debit_amount, credit_amount, created_at)
                values (?, ?, ?, ?, ?, ?)
                """, transactionId, ledgerId, currency, new BigDecimal(debit), new BigDecimal(credit), Timestamp.from(Instant.now()));
    }

    private void post(long transactionId) {
        jdbc.update("update journal_transaction set status = 'POSTED', posted_at = ? where id = ?",
                Timestamp.from(Instant.now()), transactionId);
    }

    private void markReversed(long transactionId) {
        jdbc.update("update journal_transaction set status = 'REVERSED', posted_at = ? where id = ?",
                Timestamp.from(Instant.now()), transactionId);
    }

    private void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }

    private <T> T as(String username, Callable<T> action) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                username, "", List.of(() -> "ROLE_USER")));
        try { return action.call(); }
        catch (RuntimeException exception) { throw exception; }
        catch (Exception exception) { throw new RuntimeException(exception); }
        finally { SecurityContextHolder.clearContext(); }
    }
}
