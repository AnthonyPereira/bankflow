package com.bankflow.banking.application.idempotency;

public class IdempotencyKeyReusedException extends RuntimeException {

    public IdempotencyKeyReusedException() {
        super("Cette clé a déjà servi pour une autre demande.");
    }
}
