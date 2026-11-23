package com.bankflow.banking.domain.money;

/** Une opération a mélangé deux devises : elle n'a pas de sens sans taux de change. */
public class CurrencyMismatchException extends RuntimeException {

    public CurrencyMismatchException(Currency left, Currency right) {
        super("Les devises " + left + " et " + right + " ne peuvent pas être combinées.");
    }
}
