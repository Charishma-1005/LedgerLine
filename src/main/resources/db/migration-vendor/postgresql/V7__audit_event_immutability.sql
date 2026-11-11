CREATE FUNCTION ledgerline_prevent_audit_event_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Audit events are append-only';
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_event_immutable
    BEFORE UPDATE OR DELETE ON audit_event
    FOR EACH ROW EXECUTE FUNCTION ledgerline_prevent_audit_event_mutation();

CREATE TRIGGER trg_audit_event_no_truncate
    BEFORE TRUNCATE ON audit_event
    FOR EACH STATEMENT EXECUTE FUNCTION ledgerline_prevent_audit_event_mutation();
