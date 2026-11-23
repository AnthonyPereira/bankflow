package com.bankflow.customer.application.customer;

import java.util.UUID;

/** Appelant authentifié, tel que décrit par son JWT : {@code sub} et rôle administrateur. */
public record CurrentUser(UUID userId, boolean admin) {}
