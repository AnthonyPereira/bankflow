package com.bankflow.banking.domain.transfer;

import com.bankflow.banking.domain.money.Currency;
import com.bankflow.banking.domain.money.Money;
import com.bankflow.banking.domain.transaction.Actor;
import com.bankflow.banking.domain.transaction.PerformedByRole;
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

/** Virement entre deux comptes de la banque. */
@Entity
@Table(name = "transfers")
public class Transfer implements Persistable<UUID> {

    @Id private UUID id;

    @Column(name = "source_account_id", nullable = false, updatable = false)
    private UUID sourceAccountId;

    @Column(name = "destination_account_id", nullable = false, updatable = false)
    private UUID destinationAccountId;

    @Column(nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 3)
    private Currency currency;

    @Column(updatable = false, length = 140)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TransferStatus status;

    @Column(name = "rejection_code", length = 64)
    private String rejectionCode;

    @Column(name = "created_by_customer_id", nullable = false, updatable = false)
    private UUID createdByCustomerId;

    @Column(name = "performed_by_user_id", nullable = false, updatable = false)
    private UUID performedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "performed_by_role", nullable = false, updatable = false, length = 16)
    private PerformedByRole performedByRole;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Transient private boolean newEntity = true;

    protected Transfer() {}

    private Transfer(
            UUID sourceAccountId,
            UUID destinationAccountId,
            Money amount,
            String description,
            UUID createdByCustomerId,
            Actor actor,
            Instant now) {
        this.id = UUID.randomUUID();
        this.sourceAccountId = sourceAccountId;
        this.destinationAccountId = destinationAccountId;
        this.amount = amount.amount();
        this.currency = amount.currency();
        this.description = description;
        this.status = TransferStatus.COMPLETED;
        this.createdByCustomerId = createdByCustomerId;
        this.performedByUserId = actor.userId();
        this.performedByRole = actor.role();
        this.createdAt = now;
        this.completedAt = now;
    }

    /** Virement exécuté sur-le-champ : les soldes sont modifiés dans la même transaction. */
    public static Transfer createCompleted(
            UUID sourceAccountId,
            UUID destinationAccountId,
            Money amount,
            String description,
            UUID sourceCustomerId,
            Actor actor,
            Instant now) {
        return new Transfer(
                sourceAccountId,
                destinationAccountId,
                amount,
                description,
                sourceCustomerId,
                actor,
                now);
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

    public UUID getSourceAccountId() {
        return sourceAccountId;
    }

    public UUID getDestinationAccountId() {
        return destinationAccountId;
    }

    public Money getAmount() {
        return new Money(amount, currency);
    }

    public Currency getCurrency() {
        return currency;
    }

    public String getDescription() {
        return description;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public String getRejectionCode() {
        return rejectionCode;
    }

    public UUID getCreatedByCustomerId() {
        return createdByCustomerId;
    }

    public PerformedByRole getPerformedByRole() {
        return performedByRole;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
