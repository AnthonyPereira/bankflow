package com.bankflow.banking.application.account;

/** Un conseiller a désigné un client que la banque ne connaît pas. */
public class CustomerNotFoundException extends RuntimeException {

    public CustomerNotFoundException() {
        super("Ce client est introuvable.");
    }
}
