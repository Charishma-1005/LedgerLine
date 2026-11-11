CREATE TRIGGER trg_journal_transaction_immutable
    BEFORE UPDATE, DELETE ON journal_transaction
    FOR EACH ROW CALL "com.ledgerline.ledger.infrastructure.H2JournalGuard";

CREATE TRIGGER trg_journal_entry_immutable
    BEFORE INSERT, UPDATE, DELETE ON journal_entry
    FOR EACH ROW CALL "com.ledgerline.ledger.infrastructure.H2JournalGuard";
