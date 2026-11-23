package com.bankflow.banking.api.transaction;

import com.bankflow.banking.application.transaction.TransactionView;
import com.bankflow.banking.domain.ledger.LedgerDirection;
import com.bankflow.banking.domain.money.Currency;
import com.bankflow.banking.domain.transaction.PerformedByRole;
import com.bankflow.banking.domain.transaction.TransactionType;
import java.time.Instant;
import java.util.UUID;

/**
 * Opération telle qu'exposée : jamais l'identifiant de la personne qui a agi, seulement son rôle.
 * {@code direction} est le sens pour le compte (CREDIT : il reçoit, DEBIT : il paie). {@code
 * counterpartyIban} est l'IBAN de l'autre compte d'un virement, sinon null.
 */
public record TransactionResponse(
        UUID id,
        UUID accountId,
        TransactionType type,
        LedgerDirection direction,
        String amount,
        Currency currency,
        String balanceAfter,
        UUID transferId,
        UUID compensatesTransactionId,
        UUID compensatedByTransactionId,
        String description,
        String counterpartyIban,
        PerformedByRole performedBy,
        Instant createdAt) {

    public static TransactionResponse createFrom(TransactionView view) {
        var transaction = view.transaction();
        return new TransactionResponse(
                transaction.getId(),
                transaction.getAccountId(),
                transaction.getType(),
                view.direction(),
                transaction.getAmount().toApiString(),
                transaction.getCurrency(),
                transaction.getBalanceAfter().toApiString(),
                transaction.getTransferId(),
                transaction.getCompensatesTransactionId(),
                view.compensatedByTransactionId(),
                transaction.getDescription(),
                view.counterpartyIban(),
                transaction.getPerformedByRole(),
                transaction.getCreatedAt());
    }
}
