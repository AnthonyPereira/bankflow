package com.bankflow.banking.application.account;

/** La référence du client n'est pas encore arrivée par Kafka. */
public class CustomerNotReadyException extends RuntimeException {

    public CustomerNotReadyException() {
        super("Votre profil est en cours de création, réessayez dans quelques secondes.");
    }
}
