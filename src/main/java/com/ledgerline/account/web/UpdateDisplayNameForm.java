package com.ledgerline.account.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UpdateDisplayNameForm {
    @NotBlank
    @Size(max = 120)
    private String displayName;

    public UpdateDisplayNameForm() { }

    public UpdateDisplayNameForm(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
}
