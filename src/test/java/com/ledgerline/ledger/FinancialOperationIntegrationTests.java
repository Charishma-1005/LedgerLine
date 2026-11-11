package com.ledgerline.ledger;

import com.ledgerline.account.application.FinancialAccountStore;
import com.ledgerline.account.domain.FinancialAccount;
import com.ledgerline.account.domain.FinancialAccountType;
import com.ledgerline.identity.application.IdentityUserStore;
import com.ledgerline.identity.application.RegistrationService;
import com.ledgerline.ledger.application.FinancialOperationService;
import com.ledgerline.ledger.application.FinancialOperationException;
import com.ledgerline.ledger.application.FinancialAccountUnavailableException;
import com.ledgerline.ledger.application.IdempotencyConflictException;
import com.ledgerline.ledger.application.InsufficientFundsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FinancialOperationIntegrationTests {
    private static final String PASSWORD = "A-Longer-Password-928!";
    private String email;
    private String otherEmail;
    private long ownerId;
    private long otherId;
    private FinancialAccount checking;
    private FinancialAccount savings;
    private FinancialAccount otherAccount;

    @Autowired private RegistrationService registration;
    @Autowired private IdentityUserStore users;
    @Autowired private FinancialAccountStore accounts;
    @Autowired private FinancialOperationService operations;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        String suffix = UUID.randomUUID().toString();
        email = "money-" + suffix + "@example.com";
        otherEmail = "other-money-" + suffix + "@example.com";
        registration.register(email, PASSWORD);
        registration.register(otherEmail, PASSWORD);
        ownerId = users.findByNormalizedEmail(email).orElseThrow().id();
        otherId = users.findByNormalizedEmail(otherEmail).orElseThrow().id();
        checking = create(ownerId, "Checking", "USD");
        savings = create(ownerId, "Savings", "USD");
        otherAccount = create(otherId, "Private", "USD");
    }

    @Test
    void depositWithdrawalAndTransferPostBalancedEntriesAndTransferUsesOnlyAssetAccounts() {
        withUser(() -> operations.deposit(checking.id(), new BigDecimal("100.00"), "USD", "deposit-1"));
        withUser(() -> operations.withdraw(checking.id(), new BigDecimal("15.00"), "USD", "withdraw-1"));
        withUser(() -> operations.transfer(checking.id(), savings.id(), new BigDecimal("25.00"), "USD", "transfer-1"));

        assertEquals(3, jdbc.queryForObject("select count(*) from journal_transaction where owner_id = ?", Integer.class, ownerId));
        assertEquals(0, jdbc.queryForObject("""
                select count(*) from (
                    select t.id, e.currency_code from journal_transaction t
                    join journal_entry e on e.journal_transaction_id = t.id
                    group by t.id, e.currency_code having sum(e.debit_amount) <> sum(e.credit_amount)
                ) unbalanced
                """, Integer.class));
        assertEquals(2, jdbc.queryForObject("""
                select count(*) from journal_entry e join journal_transaction t on t.id = e.journal_transaction_id
                where t.transaction_type = 'TRANSFER' and e.ledger_account_id in (?, ?)
                """, Integer.class, checking.ledgerAccountId(), savings.ledgerAccountId()));
        assertEquals(0, jdbc.queryForObject("""
                select count(*) from journal_entry e join journal_transaction t on t.id = e.journal_transaction_id
                join ledger_account l on l.id = e.ledger_account_id
                where t.transaction_type = 'TRANSFER' and l.account_type in ('INCOME', 'EXPENSE')
                """, Integer.class));
        assertAmount("60.00", jdbc.queryForObject("""
                select coalesce(sum(debit_amount - credit_amount), 0) from journal_entry
                where ledger_account_id = ? and currency_code = 'USD'
                """, BigDecimal.class, checking.ledgerAccountId()));
        assertAmount("25.00", jdbc.queryForObject("""
                select coalesce(sum(debit_amount - credit_amount), 0) from journal_entry
                where ledger_account_id = ? and currency_code = 'USD'
                """, BigDecimal.class, savings.ledgerAccountId()));
    }

    @Test
    void duplicateRequestReturnsOriginalAndSameKeyWithDifferentPayloadIsRejected() {
        long original = withUser(() -> operations.deposit(checking.id(), new BigDecimal("30.00"), "USD", "retry-key"));
        long replay = withUser(() -> operations.deposit(checking.id(), new BigDecimal("30.00"), "USD", "retry-key"));
        assertEquals(original, replay);
        assertThrows(IdempotencyConflictException.class, () -> withUser(() ->
                operations.deposit(checking.id(), new BigDecimal("31.00"), "USD", "retry-key")));
        assertEquals(1, jdbc.queryForObject("select count(*) from idempotency_record where owner_id = ? and idempotency_key = 'retry-key'", Integer.class, ownerId));
        assertAmount("30.00", jdbc.queryForObject("select sum(debit_amount) from journal_entry where ledger_account_id = ?", BigDecimal.class, checking.ledgerAccountId()));
    }

    @Test
    void concurrentIdenticalRequestsPostOnlyOnceAndReturnTheSameTransaction() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> attemptDeposit(ready, start));
            var second = executor.submit(() -> attemptDeposit(ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            long firstId = first.get();
            long secondId = second.get();
            assertEquals(firstId, secondId);
        }
        assertEquals(1, jdbc.queryForObject("select count(*) from journal_transaction where owner_id = ?", Integer.class, ownerId));
        assertEquals(1, jdbc.queryForObject("select count(*) from idempotency_record where owner_id = ? and idempotency_key = 'same-race-key'", Integer.class, ownerId));
        assertAmount("40.00", jdbc.queryForObject("select coalesce(sum(debit_amount), 0) from journal_entry where ledger_account_id = ?", BigDecimal.class, checking.ledgerAccountId()));
    }

    @Test
    void insufficientFundsAndInvalidCurrencyLeaveNoPostedRows() {
        assertThrows(InsufficientFundsException.class, () -> withUser(() ->
                operations.withdraw(checking.id(), new BigDecimal("1.00"), "USD", "empty-withdrawal")));
        assertThrows(FinancialOperationException.class, () -> withUser(() ->
                operations.deposit(checking.id(), new BigDecimal("1.001"), "USD", "fractional")));
        assertEquals(0, jdbc.queryForObject("select count(*) from journal_transaction where owner_id = ?", Integer.class, ownerId));
    }

    @Test
    void failedTransferDoesNotWriteEitherLeg() {
        assertThrows(InsufficientFundsException.class, () -> withUser(() ->
                operations.transfer(checking.id(), savings.id(), BigDecimal.ONE, "USD", "empty-transfer")));
        assertEquals(0, jdbc.queryForObject("select count(*) from journal_transaction where owner_id = ?", Integer.class, ownerId));
        assertAmount("0", jdbc.queryForObject("select coalesce(sum(debit_amount - credit_amount), 0) from journal_entry where ledger_account_id in (?, ?)",
                BigDecimal.class, checking.ledgerAccountId(), savings.ledgerAccountId()));
    }

    @Test
    void crossUserAccountsCannotBeUsedAndHistoryIsOwnerScoped() {
        withOtherUser(() -> operations.deposit(otherAccount.id(), new BigDecimal("50.00"), "USD", "other-deposit"));
        assertThrows(FinancialAccountUnavailableException.class, () -> withUser(() ->
                operations.withdraw(otherAccount.id(), BigDecimal.ONE, "USD", "forged-owner")));
        assertEquals(0, withUser(() -> operations.history()).size());
        assertEquals(1, withOtherUser(() -> operations.history()).size());
        assertEquals(1, jdbc.queryForObject("select count(*) from journal_transaction where owner_id = ?", Integer.class, otherId));
    }

    @Test
    void concurrentWithdrawalsCannotSpendTheSameBalanceTwice() throws Exception {
        withUser(() -> operations.deposit(checking.id(), new BigDecimal("100.00"), "USD", "seed-concurrent"));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> attemptWithdrawal("race-1", ready, start));
            var second = executor.submit(() -> attemptWithdrawal("race-2", ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            int successes = (first.get() ? 1 : 0) + (second.get() ? 1 : 0);
            assertEquals(1, successes);
        }
        assertAmount("20.00", jdbc.queryForObject("""
                select coalesce(sum(debit_amount - credit_amount), 0) from journal_entry
                where ledger_account_id = ? and currency_code = 'USD'
                """, BigDecimal.class, checking.ledgerAccountId()));
        assertEquals(2, jdbc.queryForObject("select count(*) from journal_transaction where owner_id = ?", Integer.class, ownerId));
    }

    @Test
    void databaseRejectsUnbalancedPostingAndMutationOfPostedRows() {
        long transactionId = withUser(() -> operations.deposit(checking.id(), new BigDecimal("10.00"), "USD", "immutable"));
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "update journal_transaction set description = 'changed' where id = ?", transactionId));
        Long entryId = jdbc.queryForObject("select id from journal_entry where journal_transaction_id = ? fetch first 1 row only",
                Long.class, transactionId);
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "update journal_entry set debit_amount = debit_amount + 1 where id = ?", entryId));
        assertThrows(DataAccessException.class, () -> jdbc.update("delete from journal_entry where id = ?", entryId));

        java.sql.Timestamp now = java.sql.Timestamp.from(java.time.Instant.now());
        jdbc.update("""
                insert into journal_transaction(owner_id, description, status, occurred_at, posted_at, created_at, transaction_type)
                values (?, 'Draft test', 'DRAFT', ?, null, ?, 'DEPOSIT')
                """, ownerId, now, now);
        Long draftId = jdbc.queryForObject("select id from journal_transaction where owner_id = ? and description = 'Draft test'",
                Long.class, ownerId);
        jdbc.update("""
                insert into journal_entry(journal_transaction_id, ledger_account_id, currency_code, debit_amount, credit_amount, created_at)
                values (?, ?, 'USD', 1.00, 0, ?)
                """, draftId, checking.ledgerAccountId(), now);
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "update journal_transaction set status = 'POSTED', posted_at = ? where id = ?", now, draftId));
        assertEquals("DRAFT", jdbc.queryForObject("select status from journal_transaction where id = ?", String.class, draftId));
    }

    @Test
    void transactionRoutesRequireRoleAndCsrf() throws Exception {
        mockMvc.perform(get("/transactions")).andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/transactions/deposit").with(user(email).roles("USER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/transactions").with(user(email).roles("USER")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/transactions").with(user(email).roles("GUEST")))
                .andExpect(status().isForbidden());
    }

    @Test
    void historyFiltersAndDetailsRemainOwnerScopedAndReadOnly() throws Exception {
        long depositId = withUser(() -> operations.deposit(checking.id(), new BigDecimal("18.25"), "USD", "ui-deposit"));
        withUser(() -> operations.withdraw(checking.id(), new BigDecimal("2.25"), "USD", "ui-withdraw"));
        long otherTransactionId = withOtherUser(() -> operations.deposit(otherAccount.id(), new BigDecimal("91.00"), "USD", "other-ui-deposit"));

        mockMvc.perform(get("/transactions").param("type", "DEPOSIT").with(user(email).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedType", "DEPOSIT"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("18.25")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("2.25"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("91.00"))));

        mockMvc.perform(get("/transactions/{id}", depositId).with(user(email).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("18.25")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Posted")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("cannot be edited or deleted")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("ledgerAccountId"))));

        mockMvc.perform(get("/transactions/{id}", otherTransactionId).with(user(email).roles("USER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void operationFormUsesOwnerAccountsAndSuccessfulPostShowsFeedback() throws Exception {
        mockMvc.perform(get("/transactions/new").param("operation", "TRANSFER").with(user(email).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Review transfer")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Checking · USD")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Savings · USD")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Private · USD"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("_csrf")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-confirm-submit")));

        mockMvc.perform(post("/transactions/deposit").with(user(email).roles("USER"))
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .param("accountId", Long.toString(checking.id()))
                        .param("amount", "14.50")
                        .param("currencyCode", "USD")
                        .param("idempotencyKey", "ui-post-success"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/transactions"))
                .andExpect(flash().attribute("transactionFeedback", "Deposit posted successfully."));
        assertEquals(1, jdbc.queryForObject("select count(*) from journal_transaction where owner_id = ? and status = 'POSTED'", Integer.class, ownerId));
    }

    @Test
    void failedOperationShowsErrorAndLeavesLedgerUnchanged() throws Exception {
        mockMvc.perform(post("/transactions/withdrawal").with(user(email).roles("USER"))
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .param("accountId", Long.toString(checking.id()))
                        .param("amount", "3.00")
                        .param("currencyCode", "USD")
                        .param("idempotencyKey", "ui-failed-withdraw"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("insufficient funds")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-confirm-submit")));
        assertEquals(0, jdbc.queryForObject("select count(*) from journal_transaction where owner_id = ?", Integer.class, ownerId));
    }

    @Test
    void oversizedFinancialAmountIsRejectedAtTheWebBoundary() throws Exception {
        mockMvc.perform(post("/transactions/deposit")
                        .with(user(email).roles("USER")).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .param("accountId", Long.toString(checking.id()))
                        .param("amount", "1234567890123456.00")
                        .param("currencyCode", "USD")
                        .param("idempotencyKey", "oversized-web-amount"))
                .andExpect(status().isOk());
        assertEquals(0, jdbc.queryForObject("select count(*) from journal_transaction where owner_id = ?", Integer.class, ownerId));
    }

    private boolean attemptWithdrawal(String key, CountDownLatch ready, CountDownLatch start) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(email, "", java.util.List.of(() -> "ROLE_USER")));
        try {
            ready.countDown();
            start.await();
            operations.withdraw(checking.id(), new BigDecimal("80.00"), "USD", key);
            return true;
        } catch (InsufficientFundsException exception) {
            return false;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private long attemptDeposit(CountDownLatch ready, CountDownLatch start) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(email, "", java.util.List.of(() -> "ROLE_USER")));
        try {
            ready.countDown();
            start.await();
            return operations.deposit(checking.id(), new BigDecimal("40.00"), "USD", "same-race-key");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private FinancialAccount create(long owner, String name, String currency) {
        return accounts.createForOwner(owner, name, FinancialAccountType.BANK, currency,
                "TEST-" + UUID.randomUUID());
    }

    private void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }

    private <T> T withUser(java.util.concurrent.Callable<T> action) {
        return with(email, action);
    }

    private <T> T withOtherUser(java.util.concurrent.Callable<T> action) {
        return with(otherEmail, action);
    }

    private <T> T with(String username, java.util.concurrent.Callable<T> action) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(username, "", java.util.List.of(() -> "ROLE_USER")));
        try { return action.call(); }
        catch (RuntimeException exception) { throw exception; }
        catch (Exception exception) { throw new RuntimeException(exception); }
        finally { SecurityContextHolder.clearContext(); }
    }
}
