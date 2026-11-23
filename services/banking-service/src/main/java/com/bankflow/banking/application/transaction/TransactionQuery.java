package com.bankflow.banking.application.transaction;

import com.bankflow.banking.domain.transaction.TransactionType;
import java.time.LocalDate;
import java.util.UUID;

/** Filtres de l'historique ; {@code customerId} n'est pris en compte que pour un admin. */
public record TransactionQuery(
        UUID accountId,
        TransactionType type,
        LocalDate from,
        LocalDate to,
        UUID customerId,
        int page,
        int size) {}
