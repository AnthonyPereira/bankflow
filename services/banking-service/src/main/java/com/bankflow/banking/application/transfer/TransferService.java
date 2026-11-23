package com.bankflow.banking.application.transfer;

import com.bankflow.banking.application.account.AccountNotFoundException;
import com.bankflow.banking.application.account.CustomerSuspendedException;
import com.bankflow.banking.application.operation.LedgerWriter;
import com.bankflow.banking.application.operation.OperationGuard;
import com.bankflow.banking.domain.account.Account;
import com.bankflow.banking.domain.account.AccountClosedException;
import com.bankflow.banking.domain.account.AccountRepository;
import com.bankflow.banking.domain.customer.CustomerRef;
import com.bankflow.banking.domain.customer.CustomerRefRepository;
import com.bankflow.banking.domain.ledger.LedgerDirection;
import com.bankflow.banking.domain.money.Money;
import com.bankflow.banking.domain.transaction.Actor;
import com.bankflow.banking.domain.transaction.Transaction;
import com.bankflow.banking.domain.transaction.TransactionType;
import com.bankflow.banking.domain.transfer.Transfer;
import com.bankflow.banking.domain.transfer.TransferRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Virements entre comptes. Les deux comptes sont verrouillés par identifiant croissant, quel que
 * soit le sens du virement : deux virements croisés ne peuvent pas s'interbloquer.
 */
@Service
public class TransferService {

    private final OperationGuard guard;
    private final AccountRepository accounts;
    private final CustomerRefRepository customerRefs;
    private final TransferRepository transfers;
    private final LedgerWriter ledgerWriter;
    private final Clock clock;

    TransferService(
            OperationGuard guard,
            AccountRepository accounts,
            CustomerRefRepository customerRefs,
            TransferRepository transfers,
            LedgerWriter ledgerWriter,
            Clock clock) {
        this.guard = guard;
        this.accounts = accounts;
        this.customerRefs = customerRefs;
        this.transfers = transfers;
        this.ledgerWriter = ledgerWriter;
        this.clock = clock;
    }

    /**
     * @param destinationIban IBAN normalisé (sans espaces, en majuscules)
     */
    @Transactional
    public TransferView createTransfer(
            Actor actor,
            UUID sourceAccountId,
            String destinationIban,
            BigDecimal amount,
            String description) {
        var destinationId = accounts.findIdByAccountNumber(destinationIban).orElse(null);
        var locked = lockAccounts(sourceAccountId, destinationId);

        // Ordre des contrôles : source, destination, même compte, client, état, devise, solde
        var source = locked.get(sourceAccountId);
        if (source == null) {
            throw new AccountNotFoundException();
        }
        guard.requireVisible(actor, source);
        var destination = destinationId == null ? null : locked.get(destinationId);
        if (destination == null || destination.isClosed()) {
            throw new DestinationAccountNotFoundException();
        }
        if (source.getId().equals(destination.getId())) {
            throw new SameAccountTransferException();
        }
        if (customerRefs
                .findById(source.getCustomerId())
                .map(CustomerRef::isSuspended)
                .orElse(false)) {
            throw new CustomerSuspendedException();
        }
        if (source.isClosed()) {
            throw new AccountClosedException();
        }
        if (source.getCurrency() != destination.getCurrency()) {
            throw new TransferCurrencyMismatchException();
        }

        var money = new Money(amount, source.getCurrency());
        source.debit(money);
        destination.credit(money);
        accounts.saveAndFlush(source);
        accounts.saveAndFlush(destination);

        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        var transfer =
                transfers.saveAndFlush(
                        Transfer.createCompleted(
                                source.getId(),
                                destination.getId(),
                                money,
                                description,
                                source.getCustomerId(),
                                actor,
                                now));
        saveLeg(transfer, source, TransactionType.TRANSFER_OUT, LedgerDirection.DEBIT, actor, now);
        saveLeg(
                transfer,
                destination,
                TransactionType.TRANSFER_IN,
                LedgerDirection.CREDIT,
                actor,
                now);
        return new TransferView(
                transfer, source.getAccountNumber(), destination.getAccountNumber(), true);
    }

    /** Titulaire de la source ou de la destination, ou administrateur ; sinon introuvable. */
    @Transactional(readOnly = true)
    public TransferView getTransfer(Actor actor, UUID transferId) {
        var transfer = transfers.findById(transferId).orElseThrow(TransferNotFoundException::new);
        var source = accounts.findById(transfer.getSourceAccountId()).orElseThrow();
        var destination = accounts.findById(transfer.getDestinationAccountId()).orElseThrow();
        var sourceVisible = actor.isAdmin() || isOwner(actor, source);
        if (!sourceVisible && !isOwner(actor, destination)) {
            throw new TransferNotFoundException();
        }
        return new TransferView(
                transfer, source.getAccountNumber(), destination.getAccountNumber(), sourceVisible);
    }

    private boolean isOwner(Actor actor, Account account) {
        return customerRefs
                .findByUserId(actor.userId())
                .map(ref -> ref.getCustomerId().equals(account.getCustomerId()))
                .orElse(false);
    }

    /**
     * Verrouille les comptes par UUID croissant ; {@code otherId} peut être nul ou égal au premier.
     */
    private Map<UUID, Account> lockAccounts(UUID sourceId, UUID otherId) {
        var locked = new HashMap<UUID, Account>();
        var first = sourceId;
        var second = otherId;
        if (otherId != null && otherId.compareTo(sourceId) < 0) {
            first = otherId;
            second = sourceId;
        }
        accounts.findByIdForUpdate(first)
                .ifPresent(account -> locked.put(account.getId(), account));
        if (second != null && !second.equals(first)) {
            accounts.findByIdForUpdate(second)
                    .ifPresent(account -> locked.put(account.getId(), account));
        }
        return locked;
    }

    private void saveLeg(
            Transfer transfer,
            Account account,
            TransactionType type,
            LedgerDirection direction,
            Actor actor,
            java.time.Instant now) {
        var leg =
                Transaction.createTransferLeg(
                        account.getId(),
                        type,
                        transfer.getAmount(),
                        account.getBalance(),
                        transfer.getId(),
                        transfer.getDescription(),
                        actor,
                        now);
        ledgerWriter.saveWithAccountEntry(leg, direction);
    }
}
