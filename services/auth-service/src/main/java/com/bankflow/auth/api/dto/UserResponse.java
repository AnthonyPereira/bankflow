package com.bankflow.auth.api.dto;

import com.bankflow.auth.domain.Role;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String email, Role role, Instant createdAt) {}
