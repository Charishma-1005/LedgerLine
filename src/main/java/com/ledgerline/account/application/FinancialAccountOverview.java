package com.ledgerline.account.application;

import com.ledgerline.account.domain.FinancialAccount;

import java.math.BigDecimal;

public record FinancialAccountOverview(FinancialAccount account, BigDecimal ledgerBalance) { }
