CREATE INDEX ix_audit_event_recent
    ON audit_event (created_at DESC, id DESC);
