package com.ledgerline.audit.application;

import com.ledgerline.account.application.AuthenticatedUserIdProvider;
import com.ledgerline.audit.domain.AuditEvent;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

@Service
public class AuditService {
    private final AuditEventStore events;
    private final AuthenticatedUserIdProvider authenticatedUser;

    public AuditService(AuditEventStore events, AuthenticatedUserIdProvider authenticatedUser) {
        this.events = events;
        this.authenticatedUser = authenticatedUser;
    }

    public void recordAuthenticated(String action, String targetType, long targetId, Map<String, ?> metadata) {
        events.append(authenticatedUser.requireUserId(), null, action, targetType, targetId, serialize(metadata));
    }

    public void recordFinancialOperation(long transactionId, String action, Map<String, ?> metadata) {
        long actor = authenticatedUser.requireUserId();
        events.append(actor, transactionId, action, "JOURNAL_TRANSACTION", transactionId, serialize(metadata));
    }

    public void recordRegistration(long userId) {
        events.append(null, null, "USER_REGISTERED", "USER", userId,
                serialize(Map.of("role", "USER", "source", "self_registration")));
    }

    public void recordSystem(String action, String targetType, long targetId, Map<String, ?> metadata) {
        events.append(null, null, action, targetType, targetId, serialize(metadata));
    }

    public List<AuditEvent> recentEvents() {
        return events.findRecent(100);
    }

    private String serialize(Map<String, ?> metadata) {
        StringJoiner fields = new StringJoiner(",", "{", "}");
        metadata.forEach((key, value) -> fields.add(quote(key) + ":" + quote(String.valueOf(value))));
        String json = fields.toString();
        if (json.length() > 2000) throw new IllegalArgumentException("Audit metadata is too large");
        return json;
    }

    private String quote(String value) {
        StringBuilder escaped = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) escaped.append(String.format("\\u%04x", (int) character));
                    else escaped.append(character);
                }
            }
        }
        return escaped.append('"').toString();
    }
}
