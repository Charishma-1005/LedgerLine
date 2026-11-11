package com.ledgerline.account.application;

public class FinancialAccountNotFoundException extends RuntimeException {
    public FinancialAccountNotFoundException() {
        super("Financial account not found");
    }
}
