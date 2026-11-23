package com.bankflow.banking.api.security;

import com.bankflow.banking.domain.transaction.Actor;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/** Identifie la personne qui agit à partir du JWT : {@code sub} et claim {@code role}. */
public final class JwtActors {

    private static final String ADMIN_ROLE = "ROLE_ADMIN";

    private JwtActors() {}

    public static Actor createFrom(Jwt jwt) {
        return Actor.createFrom(
                UUID.fromString(jwt.getSubject()), ADMIN_ROLE.equals(jwt.getClaimAsString("role")));
    }
}
