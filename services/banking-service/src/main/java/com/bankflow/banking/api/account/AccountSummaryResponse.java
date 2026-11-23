package com.bankflow.banking.api.account;

import com.bankflow.banking.application.account.AccountSummary;
import com.bankflow.banking.domain.money.Currency;
import com.bankflow.banking.domain.money.Money;
import java.util.List;

public record AccountSummaryResponse(
        Customers customers, Accounts accounts, List<CurrencyTotal> totalsByCurrency) {

    public record Customers(long total, long active, long suspended) {}

    public record Accounts(long open, long closed) {}

    public record CurrencyTotal(Currency currency, String balance, long openAccounts) {}

    static AccountSummaryResponse createFrom(AccountSummary summary) {
        return new AccountSummaryResponse(
                new Customers(
                        summary.totalCustomers(),
                        summary.activeCustomers(),
                        summary.suspendedCustomers()),
                new Accounts(summary.openAccounts(), summary.closedAccounts()),
                summary.totalsByCurrency().stream()
                        .map(total ->
                                new CurrencyTotal(
                                        total.currency(),
                                        new Money(total.balance(), total.currency()).toApiString(),
                                        total.openAccounts()))
                        .toList());
    }
}
