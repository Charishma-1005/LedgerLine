package com.ledgerline.auditor.application;

import java.math.BigDecimal;
import java.time.Instant;

public record AuditorStatementLine(long transactionId, String ownerEmail, Instant postedAt,
                                   String transactionType, String description, String currencyCode,
                                   String ledgerAccountName, BigDecimal debitAmount, BigDecimal creditAmount) { }
