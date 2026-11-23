package com.bankflow.banking.domain.customer;

import java.util.UUID;

/** Événement interne : la référence d'un client vient d'être créée (publié avant le commit). */
public record CustomerRefCreated(UUID customerId) {}
