package com.bankflow.customer.domain.customer;

import java.time.Instant;
import java.util.UUID;

/**
 * État complet du client après un changement d'email ou de statut. {@code occurredAt} est la date
 * de dernière modification du client, c'est-à-dire la date de l'état publié.
 */
public record CustomerUpdated(
        UUID customerId,
        UUID userId,
        String email,
        CustomerStatus status,
        boolean demo,
        Instant occurredAt) {

    public static CustomerUpdated createFrom(Customer customer) {
        return new CustomerUpdated(
                customer.getId(),
                customer.getUserId(),
                customer.getEmail(),
                customer.getStatus(),
                customer.isDemo(),
                customer.getUpdatedAt());
    }
}
