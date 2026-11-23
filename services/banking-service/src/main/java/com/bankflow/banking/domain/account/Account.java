package com.bankflow.banking.domain.account;

import com.bankflow.banking.domain.money.Currency;
import com.bankflow.banking.domain.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Compte bancaire d'un client. Le solde ne change que par les opérations des lots suivants. */
@Entity
@Table(name = "accounts")
public class Account {

    /** Au plus ce nombre de comptes actifs par client : les comptes de démo sont publics. */
    public static final int MAX_ACTIVE_PER_CUSTOMER = 5;

    @Id private UUID id;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "account_number", nullable = false, updatable = false, length = 34)
    private String accountNumber;

    @Column(length = 50)
    private String label;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, updatable = false, length = 3)
    private Currency currency;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AccountStatus status;

    /** Verrou optimiste : deux opérations concurrentes sur le même solde ne s'écrasent pas. */
    @Version private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    protected Account() {}

    private Account(
            UUID customerId, String accountNumber, Currency currency, String label, Instant now) {
        this.id = UUID.randomUUID();
        this.customerId = customerId;
        this.accountNumber = accountNumber;
        this.currency = currency;
        this.label = label;
        this.balance = Money.zero(currency).amount();
        this.status = AccountStatus.ACTIVE;
        this.createdAt = now;
    }

    /** Ouvre un compte actif avec un solde nul. */
    public static Account open(
            UUID customerId, String accountNumber, Currency currency, String label, Instant now) {
        return new Account(customerId, accountNumber, currency, label, now);
    }

    /** Clôture le compte : refusée s'il l'est déjà ou si son solde n'est pas nul. */
    public void close(Instant now) {
        if (status == AccountStatus.CLOSED) {
            throw new AccountClosedException();
        }
        if (!getBalance().isZero()) {
            throw new AccountBalanceNotZeroException();
        }
        this.status = AccountStatus.CLOSED;
        this.closedAt = now;
    }

    public boolean isClosed() {
        return status == AccountStatus.CLOSED;
    }

    public Money getBalance() {
        return new Money(balance, currency);
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getLabel() {
        return label;
    }

    public Currency getCurrency() {
        return currency;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }
}
