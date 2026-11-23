package com.bankflow.banking.application.account;

public class CustomerSuspendedException extends RuntimeException {

    public CustomerSuspendedException() {
        super("Votre profil est suspendu : cette opération est refusée.");
    }
}
