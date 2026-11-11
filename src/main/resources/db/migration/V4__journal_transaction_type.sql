ALTER TABLE journal_transaction
    ADD COLUMN transaction_type VARCHAR(16);

UPDATE journal_transaction SET transaction_type = 'LEGACY';

ALTER TABLE journal_transaction
    ALTER COLUMN transaction_type SET NOT NULL;

ALTER TABLE journal_transaction
    ADD CONSTRAINT ck_journal_transaction_type
        CHECK (transaction_type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER', 'LEGACY'));

