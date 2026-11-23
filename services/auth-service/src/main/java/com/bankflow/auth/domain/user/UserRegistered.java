package com.bankflow.auth.domain.user;

import java.time.Instant;
import java.util.UUID;

/** Fait métier émis dans la transaction d'inscription. */
public record UserRegistered(UUID userId, String email, boolean demo, Instant occurredAt) {}
