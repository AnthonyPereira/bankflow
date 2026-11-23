package com.bankflow.customer.domain.customer;

import java.time.Instant;
import java.util.UUID;

/** État complet du client après un changement d'email ou de statut. */
public record CustomerUpdated(
        UUID customerId, UUID userId, String email, CustomerStatus status, Instant occurredAt) {

    public static CustomerUpdated of(Customer customer, Instant occurredAt) {
        return new CustomerUpdated(
                customer.getId(),
                customer.getUserId(),
                customer.getEmail(),
                customer.getStatus(),
                occurredAt);
    }
}
