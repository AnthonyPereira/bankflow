package com.bankflow.banking.domain.transaction;

public class AlreadyCompensatedException extends RuntimeException {

    public AlreadyCompensatedException() {
        super("Cette opération a déjà été annulée.");
    }
}
