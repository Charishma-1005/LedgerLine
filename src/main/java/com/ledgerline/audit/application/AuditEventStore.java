package com.ledgerline.audit.application;

import com.ledgerline.audit.domain.AuditEvent;

import java.util.List;

public interface AuditEventStore {
    void append(Long actorOwnerId, Long transactionId, String action, String targetType,
                long targetId, String metadata);

    List<AuditEvent> findRecent(int limit);
}
