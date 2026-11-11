package com.ledgerline.audit.domain;

import java.time.Instant;

public record AuditEvent(long id, Long actorOwnerId, String action, String targetType,
                         long targetId, String metadata, Instant occurredAt) { }
