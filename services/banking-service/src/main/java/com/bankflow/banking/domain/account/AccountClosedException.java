package com.bankflow.banking.domain.account;

public class AccountClosedException extends RuntimeException {

    public AccountClosedException() {
        super("Ce compte est déjà clôturé.");
    }
}
