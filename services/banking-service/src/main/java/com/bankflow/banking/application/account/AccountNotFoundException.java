package com.bankflow.banking.application.account;

/** Compte inexistant ou appartenant à quelqu'un d'autre : on ne distingue pas les deux. */
public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException() {
        super("Compte introuvable.");
    }
}
