package com.ledgerline.shared.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * A monetary value with an ISO 4217 currency and that currency's exact minor-unit scale.
 * Values requiring rounding are rejected; callers must make any business rounding decision
 * explicitly before constructing a Money value.
 */
public record Money(BigDecimal amount, Currency currency) {

    private static final int DATABASE_PRECISION = 19;
    private static final int DATABASE_SCALE = 4;

    public Money {
        Objects.requireNonNull(amount, "amount is required");
        Objects.requireNonNull(currency, "currency is required");

        int scale = currency.getDefaultFractionDigits();
        if (scale < 0 || scale > DATABASE_SCALE) {
            throw new IllegalArgumentException("Currency is not supported by the configured monetary precision");
        }

        try {
            amount = amount.setScale(scale, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Amount has more fractional digits than the currency allows", exception);
        }

        if (amount.precision() > DATABASE_PRECISION
                || amount.abs().compareTo(BigDecimal.TEN.pow(DATABASE_PRECISION - DATABASE_SCALE)) >= 0) {
            throw new IllegalArgumentException("Amount exceeds NUMERIC(19, 4) precision");
        }
    }

    public static Money of(BigDecimal amount, String isoCurrencyCode) {
        Objects.requireNonNull(isoCurrencyCode, "currency code is required");
        if (!isoCurrencyCode.equals(isoCurrencyCode.toUpperCase(java.util.Locale.ROOT))) {
            throw new IllegalArgumentException("Currency code must be uppercase ISO 4217");
        }
        try {
            return new Money(amount, Currency.getInstance(isoCurrencyCode));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown ISO 4217 currency code: " + isoCurrencyCode, exception);
        }
    }
}
