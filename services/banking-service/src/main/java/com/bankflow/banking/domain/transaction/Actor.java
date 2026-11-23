package com.bankflow.banking.domain.transaction;

import java.util.UUID;

/** Personne qui effectue une opération : son identifiant (sub du JWT) et son rôle. */
public record Actor(UUID userId, PerformedByRole role) {

    public static Actor createFrom(UUID userId, boolean admin) {
        return new Actor(userId, admin ? PerformedByRole.ADMIN : PerformedByRole.CLIENT);
    }

    public boolean isAdmin() {
        return role == PerformedByRole.ADMIN;
    }
}
