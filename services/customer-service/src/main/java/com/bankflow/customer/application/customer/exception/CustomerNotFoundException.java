package com.bankflow.customer.application.customer.exception;

/** Aussi levée pour un client qui existe mais appartient à un autre : on ne révèle rien. */
public class CustomerNotFoundException extends RuntimeException {

    public CustomerNotFoundException() {
        super("Client introuvable.");
    }
}
