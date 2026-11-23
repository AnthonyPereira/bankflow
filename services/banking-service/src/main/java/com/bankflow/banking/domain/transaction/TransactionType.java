package com.bankflow.banking.domain.transaction;

/** Types de la contrainte de la table {@code transactions}. */
public enum TransactionType {
    DEPOSIT,
    WITHDRAWAL,
    TRANSFER_IN,
    TRANSFER_OUT,
    COMPENSATION
}
