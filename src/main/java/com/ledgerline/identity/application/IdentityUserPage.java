package com.ledgerline.identity.application;

import com.ledgerline.identity.domain.IdentityUser;

import java.util.List;

public record IdentityUserPage(List<IdentityUser> users, int page, int size,
                               long totalElements, int totalPages) {
    public IdentityUserPage {
        users = List.copyOf(users);
    }
}
