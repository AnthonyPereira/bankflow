package com.bankflow.banking.application.transaction;

import com.bankflow.banking.domain.transaction.Transaction;
import java.util.UUID;

/** Opération lue avec l'identifiant de son écriture compensatrice, s'il y en a une. */
public record TransactionView(Transaction transaction, UUID compensatedByTransactionId) {}
