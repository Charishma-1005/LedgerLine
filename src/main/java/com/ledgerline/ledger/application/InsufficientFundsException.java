package com.ledgerline.ledger.application;

public class InsufficientFundsException extends FinancialOperationException {
    public InsufficientFundsException() { super("The account has insufficient funds"); }
}
