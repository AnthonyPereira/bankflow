package com.bankflow.customer.api.customer;

import com.bankflow.customer.domain.customer.CustomerStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(
        @NotNull(message = "Le statut est obligatoire") CustomerStatus status) {}
