package com.bankflow.customer.application.customer;

import java.util.UUID;

/**
 * Appelant authentifié, tel que décrit par son JWT : {@code sub}, rôle administrateur et claim
 * {@code demo}.
 */
public record CurrentUser(UUID userId, boolean admin, boolean demo) {

    /** Un administrateur de démonstration n'agit que sur les clients de démonstration. */
    public boolean isRestrictedToDemo() {
        return admin && demo;
    }
}
