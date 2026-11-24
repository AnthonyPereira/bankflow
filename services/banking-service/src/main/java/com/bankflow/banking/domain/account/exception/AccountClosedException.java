package com.bankflow.banking.domain.account.exception;

public class AccountClosedException extends RuntimeException {

    public AccountClosedException() {
        super("Ce compte est déjà clôturé.");
    }
}
