package com.ledgerline.auditor.application;

import java.util.List;

public interface AuditorQueryStore {
    List<AuditorStatementLine> recentPostedStatementLines(int transactionLimit);
}
