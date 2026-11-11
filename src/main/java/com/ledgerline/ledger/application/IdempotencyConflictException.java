package com.ledgerline.ledger.application;

public class IdempotencyConflictException extends FinancialOperationException {
    public IdempotencyConflictException() { super("This idempotency key was already used for a different request"); }
}
