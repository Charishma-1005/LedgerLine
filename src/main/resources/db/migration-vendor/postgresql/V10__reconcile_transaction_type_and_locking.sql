ALTER TABLE journal_transaction
    DROP CONSTRAINT ck_journal_transaction_type;

ALTER TABLE journal_transaction
    ADD CONSTRAINT ck_journal_transaction_type
        CHECK (transaction_type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER', 'LEGACY'));

CREATE OR REPLACE FUNCTION ledgerline_guard_journal_entry() RETURNS trigger AS $$
DECLARE
    transaction_status VARCHAR(16);
BEGIN
    IF TG_OP = 'INSERT' THEN
        SELECT status INTO transaction_status FROM journal_transaction WHERE id = NEW.journal_transaction_id FOR UPDATE;
    ELSE
        SELECT status INTO transaction_status FROM journal_transaction WHERE id = OLD.journal_transaction_id FOR UPDATE;
    END IF;
    IF transaction_status IS DISTINCT FROM 'DRAFT' THEN
        RAISE EXCEPTION 'Entries for posted journal transactions are immutable';
    END IF;
    IF TG_OP = 'UPDATE' THEN
        SELECT status INTO transaction_status FROM journal_transaction WHERE id = NEW.journal_transaction_id FOR UPDATE;
        IF transaction_status IS DISTINCT FROM 'DRAFT' THEN
            RAISE EXCEPTION 'Entries can only be moved between draft transactions';
        END IF;
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
