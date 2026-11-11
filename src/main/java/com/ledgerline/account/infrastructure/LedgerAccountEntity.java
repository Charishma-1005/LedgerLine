package com.ledgerline.account.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "ledger_account")
public class LedgerAccountEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id")
    private Long ownerId;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "account_type", nullable = false, length = 16)
    private String accountType;

    @Column(name = "currency_code", nullable = false, length = 3)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String currencyCode;

    @Column(name = "is_system", nullable = false)
    private boolean system;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerAccountEntity() { }

    public LedgerAccountEntity(long ownerId, String code, String name, String accountType,
                               String currencyCode, Instant createdAt) {
        this.ownerId = ownerId;
        this.code = code;
        this.name = name;
        this.accountType = accountType;
        this.currencyCode = currencyCode;
        this.createdAt = createdAt;
        this.system = false;
    }

    public Long getId() { return id; }
}
