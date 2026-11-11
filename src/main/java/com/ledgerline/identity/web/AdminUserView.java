package com.ledgerline.identity.web;

import com.ledgerline.identity.domain.IdentityUser;

import java.time.Instant;

public record AdminUserView(long id, String email, String role, boolean enabled, Instant createdAt) {
    public static AdminUserView from(IdentityUser user) {
        return new AdminUserView(user.id(), user.normalizedEmail(), user.role().name(), user.enabled(), user.createdAt());
    }
}
