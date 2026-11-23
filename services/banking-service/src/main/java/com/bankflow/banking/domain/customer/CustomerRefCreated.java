package com.bankflow.banking.domain.customer;

import java.util.UUID;

/**
 * Événement interne : la référence d'un client vient d'être créée. Publié dans la transaction, il
 * est traité après le commit ({@code AFTER_COMMIT}).
 */
public record CustomerRefCreated(UUID customerId) {}
