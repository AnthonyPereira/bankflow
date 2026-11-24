package com.bankflow.auth.application.login.exception;

/** Le message reste identique dans tous les cas pour ne pas révéler quels comptes existent. */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Email ou mot de passe incorrect.");
    }
}
