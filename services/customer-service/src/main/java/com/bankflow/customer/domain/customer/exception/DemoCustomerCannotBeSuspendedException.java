package com.bankflow.customer.domain.customer.exception;

/** Un client de démonstration reste toujours utilisable : ses identifiants sont publics. */
public class DemoCustomerCannotBeSuspendedException extends RuntimeException {

    public DemoCustomerCannotBeSuspendedException() {
        super("Un client de démonstration ne peut pas être suspendu.");
    }
}
