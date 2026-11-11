package com.ledgerline.ledger.application;

public class FinancialOperationException extends RuntimeException {
    public FinancialOperationException(String message) { super(message); }
    public FinancialOperationException(String message, Throwable cause) { super(message, cause); }
}
