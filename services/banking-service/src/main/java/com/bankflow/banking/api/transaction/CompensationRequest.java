package com.bankflow.banking.api.transaction;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Motif de l'annulation, nettoyé avant validation. */
public record CompensationRequest(
        @NotNull(message = "Le motif est obligatoire.")
                @Size(
                        min = 10,
                        max = 200,
                        message = "Le motif doit contenir entre 10 et 200 caractères.")
                String reason) {

    public CompensationRequest {
        if (reason != null) {
            reason = reason.strip();
        }
    }
}
