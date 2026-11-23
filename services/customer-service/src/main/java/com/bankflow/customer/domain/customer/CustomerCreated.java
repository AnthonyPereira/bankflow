package com.bankflow.customer.domain.customer;

import java.time.Instant;
import java.util.UUID;

/**
 * État complet du client, publié sur Kafka après validation de la transaction. {@code occurredAt}
 * est la date de dernière modification du client : elle date l'état publié (même lors d'une
 * republication), ce qui permet aux consommateurs d'ignorer un événement plus ancien que celui déjà
 * appliqué.
 */
public record CustomerCreated(
        UUID customerId, UUID userId, String email, CustomerStatus status, Instant occurredAt) {

    public static CustomerCreated createFrom(Customer customer) {
        return new CustomerCreated(
                customer.getId(),
                customer.getUserId(),
                customer.getEmail(),
                customer.getStatus(),
                customer.getUpdatedAt());
    }
}
