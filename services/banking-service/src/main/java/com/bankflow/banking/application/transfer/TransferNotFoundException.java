package com.bankflow.banking.application.transfer;

/** Virement inexistant ou que l'appelant n'a pas le droit de voir. */
public class TransferNotFoundException extends RuntimeException {

    public TransferNotFoundException() {
        super("Virement introuvable.");
    }
}
