package com.ledgerline.identity.infrastructure;

import com.ledgerline.identity.domain.IdentityUser;
import com.ledgerline.identity.domain.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "app_user")
public class UserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, length = 320)
    private String email;

    @Column(name = "email_normalized", nullable = false, length = 320)
    private String emailNormalized;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "role", nullable = false, length = 16)
    private String role = UserRole.USER.name();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UserEntity() { }

    public UserEntity(String email, String emailNormalized, String passwordHash, Instant createdAt) {
        this.email = email;
        this.emailNormalized = emailNormalized;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
        this.enabled = true;
    }

    public IdentityUser toDomain() {
        return new IdentityUser(id, email, emailNormalized, passwordHash, enabled, UserRole.valueOf(role), createdAt);
    }

    public Long getId() { return id; }
    public String getEmailNormalized() { return emailNormalized; }
    public boolean isEnabled() { return enabled; }
    public String getRole() { return role; }
    public String getPasswordHash() { return passwordHash; }
}
