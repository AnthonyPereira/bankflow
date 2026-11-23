package com.bankflow.banking.api.account;

import com.bankflow.banking.domain.money.Currency;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Le libellé est nettoyé avant validation : une chaîne vide devient « pas de libellé ». */
public record CreateAccountRequest(
        @NotNull(message = "La devise est obligatoire.") Currency currency,
        @Size(max = 50, message = "Le libellé ne peut pas dépasser 50 caractères.") String label) {

    public CreateAccountRequest {
        if (label != null) {
            label = label.strip();
            if (label.isEmpty()) {
                label = null;
            }
        }
    }
}
