package com.bankflow.banking.domain.ledger;

import com.bankflow.banking.domain.money.Currency;
import com.bankflow.banking.domain.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/** Écriture comptable en partie double. Immuable : la base refuse de la modifier. */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry implements Persistable<UUID> {

    @Id private UUID id;

    @Column(name = "transaction_id", nullable = false, updatable = false)
    private UUID transactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ledger_account", nullable = false, updatable = false, length = 16)
    private LedgerAccount ledgerAccount;

    @Column(name = "account_id", updatable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 6)
    private LedgerDirection direction;

    @Column(nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 3)
    private Currency currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Transient private boolean newEntity = true;

    protected LedgerEntry() {}

    private LedgerEntry(
            UUID transactionId,
            LedgerAccount ledgerAccount,
            UUID accountId,
            LedgerDirection direction,
            Money amount,
            Instant createdAt) {
        this.id = UUID.randomUUID();
        this.transactionId = transactionId;
        this.ledgerAccount = ledgerAccount;
        this.accountId = accountId;
        this.direction = direction;
        this.amount = amount.amount();
        this.currency = amount.currency();
        this.createdAt = createdAt;
    }

    /** Écriture sur le compte d'un client. */
    public static LedgerEntry createForCustomerAccount(
            UUID transactionId,
            UUID accountId,
            LedgerDirection direction,
            Money amount,
            Instant createdAt) {
        return new LedgerEntry(
                transactionId,
                LedgerAccount.CUSTOMER_ACCOUNT,
                accountId,
                direction,
                amount,
                createdAt);
    }

    /** Écriture sur la caisse de la banque (sans compte client). */
    public static LedgerEntry createForCash(
            UUID transactionId, LedgerDirection direction, Money amount, Instant createdAt) {
        return new LedgerEntry(
                transactionId, LedgerAccount.CASH, null, direction, amount, createdAt);
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostLoad
    @PostPersist
    void markAsPersisted() {
        this.newEntity = false;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public LedgerAccount getLedgerAccount() {
        return ledgerAccount;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public LedgerDirection getDirection() {
        return direction;
    }

    public Money getAmount() {
        return new Money(amount, currency);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
