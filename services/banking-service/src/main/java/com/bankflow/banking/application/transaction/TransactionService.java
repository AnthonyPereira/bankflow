package com.bankflow.banking.application.transaction;

import com.bankflow.banking.application.account.AccountAccess;
import com.bankflow.banking.application.account.exception.AccountNotFoundException;
import com.bankflow.banking.application.transaction.exception.InvalidPeriodException;
import com.bankflow.banking.application.transaction.exception.TransactionNotFoundException;
import com.bankflow.banking.application.transaction.model.TransactionQuery;
import com.bankflow.banking.application.transaction.model.TransactionView;
import com.bankflow.banking.domain.account.Account;
import com.bankflow.banking.domain.account.AccountRepository;
import com.bankflow.banking.domain.transaction.Actor;
import com.bankflow.banking.domain.transaction.Transaction;
import com.bankflow.banking.domain.transaction.TransactionRepository;
import com.bankflow.banking.domain.transaction.TransactionType;
import com.bankflow.banking.domain.transfer.Transfer;
import com.bankflow.banking.domain.transfer.TransferRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Historique des opérations, limité aux comptes visibles (règle de {@link AccountAccess}). */
@Service
public class TransactionService {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final Sort NEWEST_FIRST =
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final TransactionRepository transactions;
    private final AccountRepository accounts;
    private final AccountAccess access;
    private final TransferRepository transfers;

    TransactionService(
            TransactionRepository transactions,
            AccountRepository accounts,
            AccountAccess access,
            TransferRepository transfers) {
        this.transactions = transactions;
        this.accounts = accounts;
        this.access = access;
        this.transfers = transfers;
    }

    @Transactional(readOnly = true)
    public Page<TransactionView> listTransactions(Actor actor, TransactionQuery query) {
        if (query.from() != null && query.to() != null && query.from().isAfter(query.to())) {
            throw new InvalidPeriodException();
        }
        var pageable = PageRequest.of(query.page(), query.size(), NEWEST_FIRST);
        if (query.accountId() != null) {
            access.requireVisible(actor, findAccount(query.accountId()));
        }
        var scope = access.createScope(actor, query.customerId());
        if (scope.empty()) {
            return Page.empty(pageable);
        }
        var page = transactions.findAll(createFilter(query, scope), pageable);
        return new PageImpl<>(createViews(page.getContent()), pageable, page.getTotalElements());
    }

    /**
     * Un nombre fixe de requêtes pour toute la page : écritures compensatrices, opérations
     * compensées, virements et comptes de contrepartie.
     */
    private List<TransactionView> createViews(List<Transaction> page) {
        if (page.isEmpty()) {
            return List.of();
        }
        var ids = page.stream().map(Transaction::getId).toList();
        Map<UUID, UUID> compensatedBy =
                transactions.findByCompensatesTransactionIdIn(ids).stream()
                        .collect(
                                Collectors.toMap(
                                        Transaction::getCompensatesTransactionId,
                                        Transaction::getId));
        var compensatedIds =
                page.stream()
                        .map(Transaction::getCompensatesTransactionId)
                        .filter(Objects::nonNull)
                        .toList();
        Map<UUID, TransactionType> compensatedTypes =
                transactions.findAllById(compensatedIds).stream()
                        .collect(Collectors.toMap(Transaction::getId, Transaction::getType));
        var counterparties = findCounterpartyIbans(page);
        return page.stream()
                .map(tx ->
                        TransactionView.createFor(
                                        tx,
                                        compensatedBy.get(tx.getId()),
                                        compensatedTypes.get(tx.getCompensatesTransactionId()))
                                .copyWithCounterpartyIban(counterparties.get(tx.getId())))
                .toList();
    }

    /** IBAN de l'autre compte de chaque virement de la page, par identifiant de transaction. */
    private Map<UUID, String> findCounterpartyIbans(List<Transaction> page) {
        var transferIds =
                page.stream().map(Transaction::getTransferId).filter(Objects::nonNull).toList();
        if (transferIds.isEmpty()) {
            return Map.of();
        }
        var transfersById =
                transfers.findAllById(transferIds).stream()
                        .collect(Collectors.toMap(Transfer::getId, Function.identity()));
        var accountIds = new HashSet<UUID>();
        transfersById
                .values()
                .forEach(transfer -> {
                    accountIds.add(transfer.getSourceAccountId());
                    accountIds.add(transfer.getDestinationAccountId());
                });
        Map<UUID, String> numbers =
                accounts.findAllById(accountIds).stream()
                        .collect(Collectors.toMap(Account::getId, Account::getAccountNumber));
        var result = new HashMap<UUID, String>();
        for (var tx : page) {
            var transfer = transfersById.get(tx.getTransferId());
            if (transfer != null) {
                var otherId =
                        tx.getType() == TransactionType.TRANSFER_OUT
                                ? transfer.getDestinationAccountId()
                                : transfer.getSourceAccountId();
                result.put(tx.getId(), numbers.get(otherId));
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public TransactionView getTransaction(Actor actor, UUID transactionId) {
        var transaction =
                transactions.findById(transactionId).orElseThrow(TransactionNotFoundException::new);
        var account = accounts.findById(transaction.getAccountId());
        if (account.isEmpty()) {
            throw new TransactionNotFoundException();
        }
        try {
            access.requireVisible(actor, account.get());
        } catch (AccountNotFoundException exception) {
            throw new TransactionNotFoundException();
        }
        return createViews(List.of(transaction)).getFirst();
    }

    private Account findAccount(UUID accountId) {
        return accounts.findById(accountId).orElseThrow(AccountNotFoundException::new);
    }

    private static Specification<Transaction> createFilter(
            TransactionQuery query, AccountAccess.Scope scope) {
        return (root, criteria, builder) -> {
            var predicates = new ArrayList<Predicate>();
            if (query.accountId() != null) {
                predicates.add(builder.equal(root.get("accountId"), query.accountId()));
            }
            if (scope.hasFilter()) {
                var visible = criteria.subquery(UUID.class);
                var account = visible.from(Account.class);
                visible.select(account.get("id"))
                        .where(scope.createPredicate(builder, visible, account.get("customerId")));
                predicates.add(root.get("accountId").in(visible));
            }
            if (query.type() != null) {
                predicates.add(builder.equal(root.get("type"), query.type()));
            }
            if (query.from() != null) {
                Instant start = query.from().atStartOfDay(PARIS).toInstant();
                predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), start));
            }
            if (query.to() != null) {
                Instant end = query.to().plusDays(1).atStartOfDay(PARIS).toInstant();
                predicates.add(builder.lessThan(root.get("createdAt"), end));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
