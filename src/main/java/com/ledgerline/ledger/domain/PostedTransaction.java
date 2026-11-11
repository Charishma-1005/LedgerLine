package com.ledgerline.ledger.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record PostedTransaction(long id, TransactionType type, String description,
                                BigDecimal amount, String currencyCode, Instant occurredAt) { }
