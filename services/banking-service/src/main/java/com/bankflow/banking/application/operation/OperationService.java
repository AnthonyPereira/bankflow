package com.bankflow.banking.application.operation;

import com.bankflow.banking.domain.account.AccountRepository;
import com.bankflow.banking.domain.ledger.LedgerDirection;
import com.bankflow.banking.domain.money.Money;
import com.bankflow.banking.domain.transaction.Actor;
import com.bankflow.banking.domain.transaction.Transaction;
import com.bankflow.banking.domain.transaction.TransactionType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dépôts et retraits. Le compte est verrouillé, son solde et les écritures sont écrits dans la même
 * transaction : le solde ne devient jamais négatif, même sous charge.
 */
@Service
public class OperationService {

    private final OperationGuard guard;
    private final AccountRepository accounts;
    private final LedgerWriter ledgerWriter;
    private final Clock clock;

    OperationService(
            OperationGuard guard,
            AccountRepository accounts,
            LedgerWriter ledgerWriter,
            Clock clock) {
        this.guard = guard;
        this.accounts = accounts;
        this.ledgerWriter = ledgerWriter;
        this.clock = clock;
    }

    @Transactional
    public Transaction deposit(Actor actor, UUID accountId, BigDecimal amount, String description) {
        return apply(actor, accountId, amount, description, TransactionType.DEPOSIT);
    }

    @Transactional
    public Transaction withdraw(
            Actor actor, UUID accountId, BigDecimal amount, String description) {
        return apply(actor, accountId, amount, description, TransactionType.WITHDRAWAL);
    }

    /** Verrouille le compte, met le solde à jour et écrit l'opération avec ses écritures. */
    private Transaction apply(
            Actor actor,
            UUID accountId,
            BigDecimal amount,
            String description,
            TransactionType type) {
        var account = guard.lockUsableAccount(actor, accountId);
        var money = new Money(amount, account.getCurrency());
        LedgerDirection direction;
        if (type == TransactionType.DEPOSIT) {
            account.credit(money);
            direction = LedgerDirection.CREDIT;
        } else {
            account.debit(money);
            direction = LedgerDirection.DEBIT;
        }
        accounts.saveAndFlush(account);
        var transaction =
                Transaction.createOperation(
                        accountId,
                        type,
                        money,
                        account.getBalance(),
                        description,
                        actor,
                        clock.instant().truncatedTo(ChronoUnit.MICROS));
        return ledgerWriter.saveWithEntries(transaction, direction);
    }
}
