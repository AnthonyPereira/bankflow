package com.bankflow.banking.api.transfer.dto;

import com.bankflow.banking.application.transfer.model.TransferView;
import com.bankflow.banking.domain.money.Currency;
import com.bankflow.banking.domain.transaction.PerformedByRole;
import com.bankflow.banking.domain.transfer.TransferStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Virement tel qu'exposé. {@code sourceAccountId} est nul pour le seul titulaire de la destination.
 */
public record TransferResponse(
        UUID id,
        UUID sourceAccountId,
        String sourceIban,
        UUID destinationAccountId,
        String destinationIban,
        String amount,
        Currency currency,
        String description,
        TransferStatus status,
        PerformedByRole performedBy,
        Instant createdAt,
        Instant completedAt) {

    public static TransferResponse createFrom(TransferView view) {
        var transfer = view.transfer();
        return new TransferResponse(
                transfer.getId(),
                view.sourceVisible() ? transfer.getSourceAccountId() : null,
                view.sourceIban(),
                transfer.getDestinationAccountId(),
                view.destinationIban(),
                transfer.getAmount().toApiString(),
                transfer.getCurrency(),
                transfer.getDescription(),
                transfer.getStatus(),
                transfer.getPerformedByRole(),
                transfer.getCreatedAt(),
                transfer.getCompletedAt());
    }
}
