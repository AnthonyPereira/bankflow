package com.bankflow.banking.application.transaction;

public class InvalidPeriodException extends RuntimeException {

    public InvalidPeriodException() {
        super("La date de début doit précéder la date de fin.");
    }
}
