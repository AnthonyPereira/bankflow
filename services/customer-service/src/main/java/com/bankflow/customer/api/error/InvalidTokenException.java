package com.bankflow.customer.api.error;

/** Le jeton est signé et valide, mais son {@code sub} n'est pas un identifiant d'utilisateur. */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException() {
        super("Authentification requise ou jeton invalide.");
    }
}
