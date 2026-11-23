package com.bankflow.banking.application.transaction;

import com.bankflow.banking.application.account.AccountNotFoundException;
import com.bankflow.banking.application.operation.OperationGuard;
import com.bankflow.banking.domain.account.Account;
import com.bankflow.banking.domain.account.AccountRepository;
import com.bankflow.banking.domain.customer.CustomerRefRepository;
import com.bankflow.banking.domain.transaction.Actor;
import com.bankflow.banking.domain.transaction.Transaction;
import com.bankflow.banking.domain.transaction.TransactionRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Historique des opérations : un client ne voit que ses comptes, un admin voit tout. */
@Service
public class TransactionService {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final Sort NEWEST_FIRST =
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final TransactionRepository transactions;
    private final AccountRepository accounts;
    private final CustomerRefRepository customerRefs;
    private final OperationGuard guard;

    TransactionService(
            TransactionRepository transactions,
            AccountRepository accounts,
            CustomerRefRepository customerRefs,
            OperationGuard guard) {
        this.transactions = transactions;
        this.accounts = accounts;
        this.customerRefs = customerRefs;
        this.guard = guard;
    }

    @Transactional(readOnly = true)
    public Page<TransactionView> listTransactions(Actor actor, TransactionQuery query) {
        if (query.from() != null && query.to() != null && query.from().isAfter(query.to())) {
            throw new InvalidPeriodException();
        }
        var pageable = PageRequest.of(query.page(), query.size(), NEWEST_FIRST);
        if (query.accountId() != null) {
            guard.requireVisible(actor, findAccount(query.accountId()));
        }
        UUID ownerCustomerId = query.customerId();
        if (!actor.isAdmin()) {
            var customer = customerRefs.findByUserId(actor.userId());
            if (customer.isEmpty()) {
                return Page.empty(pageable);
            }
            ownerCustomerId = customer.get().getCustomerId();
        }
        var page = transactions.findAll(createFilter(query, ownerCustomerId), pageable);
        var ids = page.getContent().stream().map(Transaction::getId).toList();
        var compensatedBy =
                ids.isEmpty()
                        ? java.util.Map.<UUID, UUID>of()
                        : transactions.findByCompensatesTransactionIdIn(ids).stream()
                                .collect(
                                        Collectors.toMap(
                                                Transaction::getCompensatesTransactionId,
                                                Transaction::getId));
        return new PageImpl<>(
                page.getContent().stream()
                        .map(tx -> new TransactionView(tx, compensatedBy.get(tx.getId())))
                        .toList(),
                pageable,
                page.getTotalElements());
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
            guard.requireVisible(actor, account.get());
        } catch (AccountNotFoundException exception) {
            throw new TransactionNotFoundException();
        }
        var compensatedBy =
                transactions.findByCompensatesTransactionId(transactionId).map(Transaction::getId);
        return new TransactionView(transaction, compensatedBy.orElse(null));
    }

    private Account findAccount(UUID accountId) {
        return accounts.findById(accountId).orElseThrow(AccountNotFoundException::new);
    }

    private static Specification<Transaction> createFilter(
            TransactionQuery query, UUID ownerCustomerId) {
        return (root, criteria, builder) -> {
            var predicates = new ArrayList<Predicate>();
            if (query.accountId() != null) {
                predicates.add(builder.equal(root.get("accountId"), query.accountId()));
            }
            if (ownerCustomerId != null) {
                var owned = criteria.subquery(UUID.class);
                var account = owned.from(Account.class);
                owned.select(account.get("id"))
                        .where(builder.equal(account.get("customerId"), ownerCustomerId));
                predicates.add(root.get("accountId").in(owned));
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
