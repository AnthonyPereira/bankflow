package com.bankflow.banking.application.idempotency.model;

/** Réponse mémorisée avec la clé d'idempotence : statut HTTP et corps JSON. */
public record StoredResponse(int status, String body) {}
