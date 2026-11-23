package com.bankflow.customer.api.customer;

/** Le jeton est signé et valide, mais son {@code sub} n'est pas un identifiant d'utilisateur. */
class InvalidTokenException extends RuntimeException {

    InvalidTokenException() {
        super("Authentification requise ou jeton invalide.");
    }
}
