package com.ledgerline.account.domain;

import java.time.Instant;

public record FinancialAccount(
        Long id,
        long ownerId,
        Long ledgerAccountId,
        String displayName,
        FinancialAccountType type,
        String currencyCode,
        Instant createdAt,
        boolean active) {
}
