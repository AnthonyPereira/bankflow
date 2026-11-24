package com.bankflow.banking.domain.money;

import com.bankflow.banking.domain.money.exception.CurrencyMismatchException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Montant dans une devise. Il est normalisé à 4 décimales, comme la base ; un montant qui en
 * demanderait davantage est refusé plutôt qu'arrondi en silence.
 */
public record Money(BigDecimal amount, Currency currency) {

    private static final int STORAGE_SCALE = 4;
    private static final int API_SCALE = 2;

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        amount = amount.setScale(STORAGE_SCALE, RoundingMode.UNNECESSARY);
    }

    public static Money createZero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    /**
     * Montant en chaîne décimale à 2 décimales, tel qu'il est exposé en JSON. Les montants entrants
     * seront limités à 2 décimales : l'arrondi ne joue que sur des valeurs déjà exactes.
     */
    public String toApiString() {
        return amount.setScale(API_SCALE, RoundingMode.HALF_UP).toPlainString();
    }

    private void requireSameCurrency(Money other) {
        if (currency != other.currency) {
            throw new CurrencyMismatchException(currency, other.currency);
        }
    }
}
