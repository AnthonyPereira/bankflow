package com.bankflow.banking.domain.transaction;

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

/**
 * Opération sur un compte. Elle est immuable : la base refuse toute modification ou suppression
 * (une erreur se corrige par une écriture compensatrice).
 */
@Entity
@Table(name = "transactions")
public class Transaction implements Persistable<UUID> {

    @Id private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private TransactionType type;

    @Column(nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 3)
    private Currency currency;

    @Column(name = "balance_after", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal balanceAfter;

    @Column(name = "transfer_id", updatable = false)
    private UUID transferId;

    @Column(name = "compensates_transaction_id", updatable = false)
    private UUID compensatesTransactionId;

    @Column(updatable = false, length = 140)
    private String description;

    @Column(name = "performed_by_user_id", nullable = false, updatable = false)
    private UUID performedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "performed_by_role", nullable = false, updatable = false, length = 16)
    private PerformedByRole performedByRole;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Transient private boolean newEntity = true;

    protected Transaction() {}

    private Transaction(
            UUID accountId,
            TransactionType type,
            Money amount,
            Money balanceAfter,
            UUID transferId,
            UUID compensatesTransactionId,
            String description,
            Actor actor,
            Instant now) {
        this.id = UUID.randomUUID();
        this.accountId = accountId;
        this.type = type;
        this.amount = amount.amount();
        this.currency = amount.currency();
        this.balanceAfter = balanceAfter.amount();
        this.transferId = transferId;
        this.compensatesTransactionId = compensatesTransactionId;
        this.description = description;
        this.performedByUserId = actor.userId();
        this.performedByRole = actor.role();
        this.createdAt = now;
    }

    /** Dépôt ou retrait : {@code balanceAfter} est le solde du compte après l'opération. */
    public static Transaction createOperation(
            UUID accountId,
            TransactionType type,
            Money amount,
            Money balanceAfter,
            String description,
            Actor actor,
            Instant now) {
        return new Transaction(
                accountId, type, amount, balanceAfter, null, null, description, actor, now);
    }

    /** Un côté d'un virement : {@code TRANSFER_OUT} sur la source, {@code TRANSFER_IN} ailleurs. */
    public static Transaction createTransferLeg(
            UUID accountId,
            TransactionType type,
            Money amount,
            Money balanceAfter,
            UUID transferId,
            String description,
            Actor actor,
            Instant now) {
        return new Transaction(
                accountId, type, amount, balanceAfter, transferId, null, description, actor, now);
    }

    /** Écriture compensatrice : même compte et même montant que l'opération annulée. */
    public static Transaction createCompensation(
            Transaction original, Money balanceAfter, String reason, Actor actor, Instant now) {
        return new Transaction(
                original.accountId,
                TransactionType.COMPENSATION,
                original.getAmount(),
                balanceAfter,
                null,
                original.id,
                reason,
                actor,
                now);
    }

    /** Évite le SELECT préalable de Spring Data : l'identifiant est généré par l'application. */
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

    public UUID getAccountId() {
        return accountId;
    }

    public TransactionType getType() {
        return type;
    }

    public Money getAmount() {
        return new Money(amount, currency);
    }

    public Currency getCurrency() {
        return currency;
    }

    public Money getBalanceAfter() {
        return new Money(balanceAfter, currency);
    }

    public UUID getTransferId() {
        return transferId;
    }

    public UUID getCompensatesTransactionId() {
        return compensatesTransactionId;
    }

    public String getDescription() {
        return description;
    }

    public UUID getPerformedByUserId() {
        return performedByUserId;
    }

    public PerformedByRole getPerformedByRole() {
        return performedByRole;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
