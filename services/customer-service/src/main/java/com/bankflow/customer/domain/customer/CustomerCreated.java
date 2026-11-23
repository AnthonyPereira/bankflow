package com.bankflow.customer.domain.customer;

import java.time.Instant;
import java.util.UUID;

/** État complet du client, publié sur Kafka après validation de la transaction. */
public record CustomerCreated(
        UUID customerId, UUID userId, String email, CustomerStatus status, Instant occurredAt) {

    public static CustomerCreated of(Customer customer, Instant occurredAt) {
        return new CustomerCreated(
                customer.getId(),
                customer.getUserId(),
                customer.getEmail(),
                customer.getStatus(),
                occurredAt);
    }
}
