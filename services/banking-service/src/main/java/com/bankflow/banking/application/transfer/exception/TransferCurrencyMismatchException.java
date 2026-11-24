package com.bankflow.banking.application.transfer.exception;

public class TransferCurrencyMismatchException extends RuntimeException {

    public TransferCurrencyMismatchException() {
        super("Les deux comptes doivent avoir la même devise.");
    }
}
