package com.bankflow.banking.domain.transaction.exception;

public class CompensationNotAllowedException extends RuntimeException {

    public CompensationNotAllowedException() {
        super(
                "Seuls un dépôt ou un retrait peuvent être annulés ; "
                        + "un virement se corrige par un virement inverse.");
    }
}
