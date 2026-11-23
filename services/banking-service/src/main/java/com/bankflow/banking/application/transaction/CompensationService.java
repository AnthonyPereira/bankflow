package com.bankflow.banking.application.transaction;

import com.bankflow.banking.application.operation.LedgerWriter;
import com.bankflow.banking.application.operation.OperationGuard;
import com.bankflow.banking.domain.account.AccountRepository;
import com.bankflow.banking.domain.ledger.LedgerDirection;
import com.bankflow.banking.domain.transaction.Actor;
import com.bankflow.banking.domain.transaction.AlreadyCompensatedException;
import com.bankflow.banking.domain.transaction.CompensationNotAllowedException;
import com.bankflow.banking.domain.transaction.Transaction;
import com.bankflow.banking.domain.transaction.TransactionRepository;
import com.bankflow.banking.domain.transaction.TransactionType;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Annule un dépôt ou un retrait par une écriture inverse, sans jamais modifier l'historique. */
@Service
public class CompensationService {

    private final TransactionRepository transactions;
    private final AccountRepository accounts;
    private final OperationGuard guard;
    private final LedgerWriter ledgerWriter;
    private final Clock clock;

    CompensationService(
            TransactionRepository transactions,
            AccountRepository accounts,
            OperationGuard guard,
            LedgerWriter ledgerWriter,
            Clock clock) {
        this.transactions = transactions;
        this.accounts = accounts;
        this.guard = guard;
        this.ledgerWriter = ledgerWriter;
        this.clock = clock;
    }

    /** Réservé aux administrateurs (contrôlé par l'API) : {@code actor} est l'un d'eux. */
    @Transactional
    public TransactionView compensate(Actor actor, UUID transactionId, String reason) {
        var original =
                transactions.findById(transactionId).orElseThrow(TransactionNotFoundException::new);
        if (original.getType() != TransactionType.DEPOSIT
                && original.getType() != TransactionType.WITHDRAWAL) {
            throw new CompensationNotAllowedException();
        }
        // Le verrou du compte sérialise deux annulations de la même opération
        var account = guard.lockUsableAccount(actor, original.getAccountId());
        if (transactions.existsByCompensatesTransactionId(transactionId)) {
            throw new AlreadyCompensatedException();
        }
        LedgerDirection direction;
        if (original.getType() == TransactionType.DEPOSIT) {
            account.debit(original.getAmount());
            direction = LedgerDirection.DEBIT;
        } else {
            account.credit(original.getAmount());
            direction = LedgerDirection.CREDIT;
        }
        accounts.saveAndFlush(account);
        var compensation =
                Transaction.createCompensation(
                        original,
                        account.getBalance(),
                        reason,
                        actor,
                        clock.instant().truncatedTo(ChronoUnit.MICROS));
        var saved = ledgerWriter.saveWithEntries(compensation, direction);
        return new TransactionView(saved, null, direction);
    }
}
