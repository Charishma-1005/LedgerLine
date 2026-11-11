package com.ledgerline.audit.infrastructure;

import com.ledgerline.audit.application.AuditEventStore;
import com.ledgerline.audit.domain.AuditEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class JdbcAuditEventStore implements AuditEventStore {
    private final JdbcTemplate jdbc;

    public JdbcAuditEventStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void append(Long actorOwnerId, Long transactionId, String action, String targetType,
                       long targetId, String metadata) {
        jdbc.update("""
                insert into audit_event(actor_owner_id, journal_transaction_id, event_type, entity_type,
                                        entity_id, details, created_at)
                values (?, ?, ?, ?, ?, ?, ?)
                """, actorOwnerId, transactionId, action, targetType, targetId, metadata,
                Timestamp.from(Instant.now()));
    }

    @Override
    public List<AuditEvent> findRecent(int limit) {
        return jdbc.query("""
                select id, actor_owner_id, event_type, entity_type, entity_id, details, created_at
                from audit_event order by created_at desc, id desc limit ?
                """, (rs, row) -> new AuditEvent(rs.getLong("id"),
                rs.getObject("actor_owner_id", Long.class), rs.getString("event_type"),
                rs.getString("entity_type"), rs.getLong("entity_id"), rs.getString("details"),
                rs.getTimestamp("created_at").toInstant()), limit);
    }
}
