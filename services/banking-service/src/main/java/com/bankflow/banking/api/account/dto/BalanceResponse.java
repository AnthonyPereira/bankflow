package com.bankflow.banking.api.account.dto;

import com.bankflow.banking.domain.account.Account;
import com.bankflow.banking.domain.money.Currency;
import java.time.Instant;
import java.util.UUID;

public record BalanceResponse(UUID accountId, String balance, Currency currency, Instant asOf) {

    public static BalanceResponse createFrom(Account account, Instant asOf) {
        return new BalanceResponse(
                account.getId(), account.getBalance().toApiString(), account.getCurrency(), asOf);
    }
}
