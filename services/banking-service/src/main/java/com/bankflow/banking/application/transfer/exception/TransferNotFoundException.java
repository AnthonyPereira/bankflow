package com.bankflow.banking.application.transfer.exception;

/** Virement inexistant ou que l'appelant n'a pas le droit de voir. */
public class TransferNotFoundException extends RuntimeException {

    public TransferNotFoundException() {
        super("Virement introuvable.");
    }
}
