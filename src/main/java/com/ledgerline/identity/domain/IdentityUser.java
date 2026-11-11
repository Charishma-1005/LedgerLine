package com.ledgerline.identity.domain;

import java.time.Instant;
import java.util.Objects;

/** Identity data used by application and authentication adapters. Never expose this object to web views. */
public final class IdentityUser {
    private final Long id;
    private final String email;
    private final String normalizedEmail;
    private final String passwordHash;
    private final boolean enabled;
    private final UserRole role;
    private final Instant createdAt;

    public IdentityUser(Long id, String email, String normalizedEmail, String passwordHash,
                        boolean enabled, UserRole role, Instant createdAt) {
        this.id = id;
        this.email = Objects.requireNonNull(email);
        this.normalizedEmail = Objects.requireNonNull(normalizedEmail);
        this.passwordHash = passwordHash;
        this.enabled = enabled;
        this.role = Objects.requireNonNull(role);
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public Long id() { return id; }
    public String email() { return email; }
    public String normalizedEmail() { return normalizedEmail; }
    public String passwordHash() { return passwordHash; }
    public boolean enabled() { return enabled; }
    public UserRole role() { return role; }
    public Instant createdAt() { return createdAt; }

    @Override
    public String toString() {
        return "IdentityUser[id=" + id + ", normalizedEmail=" + normalizedEmail + ", enabled=" + enabled + "]";
    }
}
