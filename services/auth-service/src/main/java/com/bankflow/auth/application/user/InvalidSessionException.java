package com.bankflow.auth.application.user;

/** Le JWT présenté désigne un compte qui n'existe plus ou qui a été désactivé. */
public class InvalidSessionException extends RuntimeException {

    public InvalidSessionException() {
        super("Session invalide. Veuillez vous reconnecter.");
    }
}
