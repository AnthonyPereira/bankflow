package com.bankflow.banking.application.operation;

import com.bankflow.banking.domain.ledger.LedgerDirection;
import com.bankflow.banking.domain.ledger.LedgerEntry;
import com.bankflow.banking.domain.ledger.LedgerEntryRepository;
import com.bankflow.banking.domain.transaction.Transaction;
import com.bankflow.banking.domain.transaction.TransactionRepository;
import org.springframework.stereotype.Component;

/** Enregistre une opération et ses deux écritures équilibrées (compte client et caisse). */
@Component
public class LedgerWriter {

    private final TransactionRepository transactions;
    private final LedgerEntryRepository ledgerEntries;

    LedgerWriter(TransactionRepository transactions, LedgerEntryRepository ledgerEntries) {
        this.transactions = transactions;
        this.ledgerEntries = ledgerEntries;
    }

    /**
     * @param accountDirection sens de l'écriture sur le compte client ; la caisse reçoit le sens
     *     opposé (dépôt : crédit du compte et débit de la caisse)
     */
    public Transaction saveWithEntries(Transaction transaction, LedgerDirection accountDirection) {
        var saved = transactions.save(transaction);
        var amount = saved.getAmount();
        ledgerEntries.save(
                LedgerEntry.createForCustomerAccount(
                        saved.getId(),
                        saved.getAccountId(),
                        accountDirection,
                        amount,
                        saved.getCreatedAt()));
        ledgerEntries.save(
                LedgerEntry.createForCash(
                        saved.getId(),
                        accountDirection.createOpposite(),
                        amount,
                        saved.getCreatedAt()));
        return saved;
    }

    /**
     * Enregistre un côté de virement avec son unique écriture sur le compte client : l'autre côté
     * du virement porte l'écriture opposée, la caisse n'intervient pas.
     */
    public Transaction saveWithAccountEntry(
            Transaction transaction, LedgerDirection accountDirection) {
        var saved = transactions.save(transaction);
        ledgerEntries.save(
                LedgerEntry.createForCustomerAccount(
                        saved.getId(),
                        saved.getAccountId(),
                        accountDirection,
                        saved.getAmount(),
                        saved.getCreatedAt()));
        return saved;
    }
}
