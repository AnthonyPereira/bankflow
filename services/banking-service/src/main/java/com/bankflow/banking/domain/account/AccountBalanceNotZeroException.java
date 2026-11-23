package com.bankflow.banking.domain.account;

public class AccountBalanceNotZeroException extends RuntimeException {

    public AccountBalanceNotZeroException() {
        super("Le compte ne peut être clôturé que si son solde est nul.");
    }
}
