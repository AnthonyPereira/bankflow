package com.bankflow.banking.application.transfer.exception;

/** Aucun compte ouvert ne porte l'IBAN demandé (inconnu ou clôturé). */
public class DestinationAccountNotFoundException extends RuntimeException {

    public DestinationAccountNotFoundException() {
        super("Aucun compte actif ne correspond à cet IBAN.");
    }
}
