package com.ledgerline.account.application;

public class InvalidCurrencyCodeException extends RuntimeException {
    public InvalidCurrencyCodeException() {
        super("Unsupported ISO 4217 currency code");
    }
}
