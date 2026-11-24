package com.bankflow.auth.domain.user.event;

import com.bankflow.auth.domain.user.Role;
import java.time.Instant;
import java.util.UUID;

/** Fait métier émis dans la transaction d'inscription. */
public record UserRegistered(
        UUID userId, String email, boolean demo, Role role, Instant occurredAt) {}
