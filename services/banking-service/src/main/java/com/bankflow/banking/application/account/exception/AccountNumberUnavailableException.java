package com.bankflow.banking.application.account.exception;

/** Aucun numéro de compte libre n'a été trouvé après plusieurs essais. */
public class AccountNumberUnavailableException extends RuntimeException {

    public AccountNumberUnavailableException() {
        super("Impossible d'attribuer un numéro de compte, réessayez.");
    }
}
