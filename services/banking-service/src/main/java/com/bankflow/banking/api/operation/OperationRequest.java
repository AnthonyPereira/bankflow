package com.bankflow.banking.api.operation;

import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Dépôt ou retrait. La description est nettoyée avant validation (vide = absente). */
public record OperationRequest(
        @OperationAmount String amount,
        @Size(max = 140, message = "La description ne peut pas dépasser 140 caractères.")
                String description) {

    public OperationRequest {
        if (description != null) {
            description = description.strip();
            if (description.isEmpty()) {
                description = null;
            }
        }
    }

    /** À n'appeler qu'après validation. */
    BigDecimal readAmount() {
        return OperationAmountValidator.parseAmount(amount);
    }
}
