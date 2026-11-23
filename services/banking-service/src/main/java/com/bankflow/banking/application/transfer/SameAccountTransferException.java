package com.bankflow.banking.application.transfer;

public class SameAccountTransferException extends RuntimeException {

    public SameAccountTransferException() {
        super("Le compte de destination doit être différent du compte source.");
    }
}
