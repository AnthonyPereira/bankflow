package com.bankflow.banking.api.account.dto;

import com.bankflow.banking.domain.account.Account;
import com.bankflow.banking.domain.account.AccountStatus;
import com.bankflow.banking.domain.money.Currency;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String accountNumber,
        UUID customerId,
        String label,
        Currency currency,
        String balance,
        AccountStatus status,
        Instant createdAt,
        Instant closedAt) {

    public static AccountResponse createFrom(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getCustomerId(),
                account.getLabel(),
                account.getCurrency(),
                account.getBalance().toApiString(),
                account.getStatus(),
                account.getCreatedAt(),
                account.getClosedAt());
    }
}
