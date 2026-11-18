package com.bankflow.auth.application;

public class EmailAlreadyUsedException extends RuntimeException {

    public EmailAlreadyUsedException() {
        super("Cette adresse email est déjà utilisée.");
    }
}
