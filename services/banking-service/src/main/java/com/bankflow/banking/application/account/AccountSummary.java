package com.bankflow.banking.application.account;

import com.bankflow.banking.domain.money.Currency;
import java.math.BigDecimal;
import java.util.List;

/** Chiffres de la banque pour l'administrateur ; les totaux ne comptent que les comptes ouverts. */
public record AccountSummary(
        long totalCustomers,
        long activeCustomers,
        long suspendedCustomers,
        long openAccounts,
        long closedAccounts,
        List<CurrencyTotal> totalsByCurrency) {

    public record CurrencyTotal(Currency currency, BigDecimal balance, long openAccounts) {}
}
