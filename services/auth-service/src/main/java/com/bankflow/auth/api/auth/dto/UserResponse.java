package com.bankflow.auth.api.auth.dto;

import com.bankflow.auth.domain.user.Role;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String email, Role role, Instant createdAt) {}
