package com.bankflow.banking.application.transaction;

/** Opération inexistante ou sur le compte de quelqu'un d'autre : on ne distingue pas les deux. */
public class TransactionNotFoundException extends RuntimeException {

    public TransactionNotFoundException() {
        super("Opération introuvable.");
    }
}
