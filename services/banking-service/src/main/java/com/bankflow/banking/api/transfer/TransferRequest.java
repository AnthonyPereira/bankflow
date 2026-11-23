package com.bankflow.banking.api.transfer;

import com.bankflow.banking.api.operation.OperationAmount;
import com.bankflow.banking.api.operation.OperationAmountValidator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

/**
 * Virement demandé. L'IBAN est normalisé (espaces retirés, majuscules) et la description nettoyée
 * (vide = absente) avant validation.
 */
public record TransferRequest(
        @NotNull(message = "Le compte source est obligatoire.") UUID sourceAccountId,
        @NotBlank(message = "L'IBAN du compte de destination est obligatoire.")
                @Size(max = 34, message = "L'IBAN du compte de destination est invalide.")
                String destinationIban,
        @OperationAmount String amount,
        @Size(max = 140, message = "La description ne peut pas dépasser 140 caractères.")
                String description) {

    public TransferRequest {
        if (destinationIban != null) {
            destinationIban = destinationIban.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        }
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
