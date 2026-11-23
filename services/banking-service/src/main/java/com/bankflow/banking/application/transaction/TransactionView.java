package com.bankflow.banking.application.transaction;

import com.bankflow.banking.domain.ledger.LedgerDirection;
import com.bankflow.banking.domain.transaction.Transaction;
import com.bankflow.banking.domain.transaction.TransactionType;
import java.util.UUID;

/**
 * Opération lue avec l'identifiant de son écriture compensatrice (s'il y en a une), son sens pour
 * le compte et, pour un virement, l'IBAN de l'autre compte.
 */
public record TransactionView(
        Transaction transaction,
        UUID compensatedByTransactionId,
        LedgerDirection direction,
        String counterpartyIban) {

    public TransactionView(
            Transaction transaction, UUID compensatedByTransactionId, LedgerDirection direction) {
        this(transaction, compensatedByTransactionId, direction, null);
    }

    /** Ajoute l'IBAN de l'autre compte d'un virement. */
    public TransactionView withCounterpartyIban(String iban) {
        return new TransactionView(transaction, compensatedByTransactionId, direction, iban);
    }

    /**
     * @param compensatedType type de l'opération compensée, requis pour une compensation (dont le
     *     sens est l'inverse du sien), ignoré sinon
     */
    public static TransactionView createFor(
            Transaction transaction,
            UUID compensatedByTransactionId,
            TransactionType compensatedType) {
        var direction =
                transaction.getType() == TransactionType.COMPENSATION
                        ? compensatedType.getDirection().createOpposite()
                        : transaction.getType().getDirection();
        return new TransactionView(transaction, compensatedByTransactionId, direction);
    }
}
