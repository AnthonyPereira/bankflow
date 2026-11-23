package com.bankflow.banking.domain.transaction;

/** Qui a agi : le client lui-même ou un conseiller (administrateur) en son nom. */
public enum PerformedByRole {
    CLIENT,
    ADMIN
}
