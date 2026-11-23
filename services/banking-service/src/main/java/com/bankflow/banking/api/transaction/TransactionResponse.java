package com.bankflow.banking.api.transaction;

import com.bankflow.banking.domain.money.Currency;
import com.bankflow.banking.domain.transaction.PerformedByRole;
import com.bankflow.banking.domain.transaction.Transaction;
import com.bankflow.banking.domain.transaction.TransactionType;
import java.time.Instant;
import java.util.UUID;

/**
 * Opération telle qu'exposée : jamais l'identifiant de la personne qui a agi, seulement son rôle.
 */
public record TransactionResponse(
        UUID id,
        UUID accountId,
        TransactionType type,
        String amount,
        Currency currency,
        String balanceAfter,
        UUID transferId,
        UUID compensatesTransactionId,
        UUID compensatedByTransactionId,
        String description,
        PerformedByRole performedBy,
        Instant createdAt) {

    public static TransactionResponse createFrom(
            Transaction transaction, UUID compensatedByTransactionId) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getAccountId(),
                transaction.getType(),
                transaction.getAmount().toApiString(),
                transaction.getCurrency(),
                transaction.getBalanceAfter().toApiString(),
                transaction.getTransferId(),
                transaction.getCompensatesTransactionId(),
                compensatedByTransactionId,
                transaction.getDescription(),
                transaction.getPerformedByRole(),
                transaction.getCreatedAt());
    }
}
