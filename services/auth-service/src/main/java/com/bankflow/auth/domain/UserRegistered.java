package com.bankflow.auth.domain;

import java.time.Instant;
import java.util.UUID;

/** Fait métier émis dans la transaction d'inscription. */
public record UserRegistered(UUID userId, String email, Instant occurredAt) {}
