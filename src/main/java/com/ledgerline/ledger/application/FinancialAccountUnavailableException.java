package com.ledgerline.ledger.application;

public class FinancialAccountUnavailableException extends FinancialOperationException {
    public FinancialAccountUnavailableException() { super("One or more accounts are unavailable"); }
}
