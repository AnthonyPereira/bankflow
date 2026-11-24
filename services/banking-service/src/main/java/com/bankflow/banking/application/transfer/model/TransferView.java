package com.bankflow.banking.application.transfer.model;

import com.bankflow.banking.domain.transfer.Transfer;

/**
 * Virement avec les IBAN de ses deux comptes. {@code sourceVisible} est faux pour le seul titulaire
 * de la destination : il voit l'IBAN émetteur, pas l'identifiant interne du compte source.
 */
public record TransferView(
        Transfer transfer, String sourceIban, String destinationIban, boolean sourceVisible) {}
