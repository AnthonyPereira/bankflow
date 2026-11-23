package com.bankflow.banking.domain.account;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException() {
        super("Le solde est insuffisant.");
    }
}
