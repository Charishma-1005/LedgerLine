CREATE TRIGGER trg_audit_event_immutable
    BEFORE UPDATE, DELETE ON audit_event
    FOR EACH ROW CALL "com.ledgerline.audit.infrastructure.H2AuditGuard";
