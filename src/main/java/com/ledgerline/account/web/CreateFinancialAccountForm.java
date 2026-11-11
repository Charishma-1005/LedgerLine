package com.ledgerline.account.web;

import com.ledgerline.account.domain.FinancialAccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CreateFinancialAccountForm {
    @NotBlank
    @Size(max = 120)
    private String displayName;

    @NotNull
    private FinancialAccountType type;

    @NotBlank
    @Pattern(regexp = "[A-Z]{3}", message = "Enter a three-letter uppercase ISO currency code")
    private String currencyCode;

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public FinancialAccountType getType() { return type; }
    public void setType(FinancialAccountType type) { this.type = type; }
    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
}
