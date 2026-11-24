package com.bankflow.banking.domain.transfer;

/**
 * Statuts de la contrainte de la table {@code transfers}. Seul {@code COMPLETED} est produit
 * aujourd'hui : {@code PENDING} et {@code REJECTED} sont réservés à un traitement asynchrone futur.
 */
public enum TransferStatus {
    PENDING,
    COMPLETED,
    REJECTED
}
