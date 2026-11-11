CREATE FUNCTION ledgerline_guard_journal_transaction() RETURNS trigger AS $$
DECLARE
    entry_count BIGINT;
BEGIN
    IF OLD.status <> 'DRAFT' THEN
        RAISE EXCEPTION 'Posted journal transactions are immutable';
    END IF;

    IF TG_OP = 'UPDATE' AND NEW.status = 'POSTED' THEN
        SELECT COUNT(*) INTO entry_count FROM journal_entry WHERE journal_transaction_id = OLD.id;
        IF entry_count < 2 OR EXISTS (
            SELECT currency_code FROM journal_entry WHERE journal_transaction_id = OLD.id
            GROUP BY currency_code HAVING SUM(debit_amount) <> SUM(credit_amount)
        ) THEN
            RAISE EXCEPTION 'A posted journal transaction must have at least two balanced entries per currency';
        END IF;
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_journal_transaction_immutable
    BEFORE UPDATE OR DELETE ON journal_transaction
    FOR EACH ROW EXECUTE FUNCTION ledgerline_guard_journal_transaction();

CREATE FUNCTION ledgerline_guard_journal_entry() RETURNS trigger AS $$
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

CREATE TRIGGER trg_journal_entry_immutable
    BEFORE INSERT OR UPDATE OR DELETE ON journal_entry
    FOR EACH ROW EXECUTE FUNCTION ledgerline_guard_journal_entry();
