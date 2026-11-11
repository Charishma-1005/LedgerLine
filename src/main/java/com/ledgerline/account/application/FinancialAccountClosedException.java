package com.ledgerline.account.application;

public class FinancialAccountClosedException extends RuntimeException {
    public FinancialAccountClosedException() {
        super("Financial account is closed");
    }
}
