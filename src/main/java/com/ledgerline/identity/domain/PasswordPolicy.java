package com.ledgerline.identity.domain;

import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {
    private static final int MIN_CHARACTERS = 12;
    private static final int MAX_BCRYPT_BYTES = 72;

    private PasswordPolicy() { }

    public static boolean isValid(String password) {
        return password != null
                && password.length() >= MIN_CHARACTERS
                && password.getBytes(StandardCharsets.UTF_8).length <= MAX_BCRYPT_BYTES;
    }
}
