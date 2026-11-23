package com.bankflow.banking.domain.transaction;

import java.util.UUID;

/**
 * Personne qui effectue une opération : son identifiant (sub du JWT) et son rôle. {@code demo}
 * indique un compte de démonstration : un administrateur de démonstration n'agit que sur les
 * clients de démonstration.
 */
public record Actor(UUID userId, PerformedByRole role, boolean demo) {

    public static Actor createFrom(UUID userId, boolean admin) {
        return createFrom(userId, admin, false);
    }

    public static Actor createFrom(UUID userId, boolean admin, boolean demo) {
        return new Actor(userId, admin ? PerformedByRole.ADMIN : PerformedByRole.CLIENT, demo);
    }

    public boolean isAdmin() {
        return role == PerformedByRole.ADMIN;
    }

    /** Administrateur de démonstration : ne voit et ne modifie que les clients de démonstration. */
    public boolean isRestrictedToDemo() {
        return isAdmin() && demo;
    }
}
