package com.ledgerline.account.infrastructure;

import com.ledgerline.account.domain.FinancialAccount;
import com.ledgerline.account.domain.FinancialAccountType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "financial_account")
public class FinancialAccountEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private long ownerId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ledger_account_id", nullable = false)
    private LedgerAccountEntity ledgerAccount;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 24)
    private FinancialAccountType accountType;

    @Column(name = "currency_code", nullable = false, length = 3)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String currencyCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected FinancialAccountEntity() { }

    public FinancialAccountEntity(long ownerId, LedgerAccountEntity ledgerAccount, String displayName,
                                  FinancialAccountType accountType, String currencyCode, Instant createdAt) {
        this.ownerId = ownerId;
        this.ledgerAccount = ledgerAccount;
        this.displayName = displayName;
        this.accountType = accountType;
        this.currencyCode = currencyCode;
        this.createdAt = createdAt;
        this.active = true;
    }

    public FinancialAccount toDomain() {
        return new FinancialAccount(id, ownerId, ledgerAccount.getId(), displayName,
                accountType, currencyCode, createdAt, active);
    }

    public Long ledgerAccountId() {
        return ledgerAccount.getId();
    }
}
