package com.ledgerline.audit.infrastructure;

import org.h2.api.Trigger;

import java.sql.Connection;
import java.sql.SQLException;

public class H2AuditGuard implements Trigger {
    @Override
    public void init(Connection connection, String schemaName, String triggerName,
                     String tableName, boolean before, int type) { }

    @Override
    public void fire(Connection connection, Object[] oldRow, Object[] newRow) throws SQLException {
        throw new SQLException("Audit events are append-only");
    }
}
