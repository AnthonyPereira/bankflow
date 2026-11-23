package com.bankflow.banking.application.idempotency;

/** Réponse à renvoyer ; {@code replayed} vaut vrai si elle vient d'une requête déjà traitée. */
public record IdempotencyResult(StoredResponse response, boolean replayed) {}
