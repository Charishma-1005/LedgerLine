package com.ledgerline.identity.domain;

import java.util.Locale;
import java.util.Objects;

public record EmailAddress(String value, String normalized) {

    public EmailAddress {
        Objects.requireNonNull(value, "email is required");
        Objects.requireNonNull(normalized, "normalized email is required");
        if (value.isBlank() || value.length() > 320 || !normalized.equals(value.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Email address is invalid");
        }
    }

    public static EmailAddress of(String input) {
        Objects.requireNonNull(input, "email is required");
        String value = input.strip();
        String normalized = value.toLowerCase(Locale.ROOT);
        if (value.isBlank() || value.length() > 320 || value.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Email address is invalid");
        }
        return new EmailAddress(value, normalized);
    }

    public static String normalizeForLookup(String input) {
        return of(input).normalized();
    }
}
