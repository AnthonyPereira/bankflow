package com.bankflow.banking.domain.transaction;

import com.bankflow.banking.domain.ledger.LedgerDirection;

/** Types de la contrainte de la table {@code transactions}. */
public enum TransactionType {
    DEPOSIT(LedgerDirection.CREDIT),
    WITHDRAWAL(LedgerDirection.DEBIT),
    TRANSFER_IN(LedgerDirection.CREDIT),
    TRANSFER_OUT(LedgerDirection.DEBIT),
    /** Son sens est l'inverse de celui de l'opération compensée : il n'est pas fixe. */
    COMPENSATION(null);

    private final LedgerDirection direction;

    TransactionType(LedgerDirection direction) {
        this.direction = direction;
    }

    /** Sens pour le compte, ou {@code null} pour une compensation. */
    public LedgerDirection getDirection() {
        return direction;
    }
}
