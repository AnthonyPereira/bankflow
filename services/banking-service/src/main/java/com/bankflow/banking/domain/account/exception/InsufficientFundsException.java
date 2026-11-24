package com.bankflow.banking.domain.account.exception;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException() {
        super("Le solde est insuffisant.");
    }
}
