ALTER TABLE journal_transaction
    DROP CONSTRAINT ck_journal_transaction_type;

ALTER TABLE journal_transaction
    ADD CONSTRAINT ck_journal_transaction_type
        CHECK (transaction_type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER', 'LEGACY'));
