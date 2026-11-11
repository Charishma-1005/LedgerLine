package com.ledgerline.identity.web;

import com.ledgerline.identity.domain.PasswordPolicy;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordValidValidator implements ConstraintValidator<PasswordValid, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return PasswordPolicy.isValid(value);
    }
}
