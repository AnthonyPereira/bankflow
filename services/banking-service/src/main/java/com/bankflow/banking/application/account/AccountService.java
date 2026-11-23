package com.bankflow.banking.application.account;

import com.bankflow.banking.domain.account.Account;
import com.bankflow.banking.domain.account.AccountNumberGenerator;
import com.bankflow.banking.domain.account.AccountRepository;
import com.bankflow.banking.domain.account.AccountStatus;
import com.bankflow.banking.domain.customer.CustomerRef;
import com.bankflow.banking.domain.customer.CustomerRefRepository;
import com.bankflow.banking.domain.customer.CustomerStatus;
import com.bankflow.banking.domain.money.Currency;
import com.bankflow.banking.domain.transaction.Actor;
import java.time.Clock;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cas d'usage des comptes. La visibilité d'un compte suit la règle unique de {@link AccountAccess}.
 */
@Service
public class AccountService {

    private static final int MAX_NUMBER_ATTEMPTS = 3;
    private static final Sort NEWEST_FIRST =
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final AccountRepository accounts;
    private final CustomerRefRepository customerRefs;
    private final AccountAccess access;
    private final AccountNumberGenerator numberGenerator;
    private final Clock clock;

    AccountService(
            AccountRepository accounts,
            CustomerRefRepository customerRefs,
            AccountAccess access,
            AccountNumberGenerator numberGenerator,
            Clock clock) {
        this.accounts = accounts;
        this.customerRefs = customerRefs;
        this.access = access;
        this.numberGenerator = numberGenerator;
        this.clock = clock;
    }

    /** Ouvre un compte pour l'appelant lui-même (un client). */
    @Transactional
    public Account openAccount(UUID userId, Currency currency, String label) {
        var customer =
                customerRefs
                        .findByUserIdForUpdate(userId)
                        .orElseThrow(CustomerNotReadyException::new);
        return openAccountFor(customer, currency, label);
    }

    /**
     * Ouvre un compte au nom d'un client, à la demande d'un conseiller. Un conseiller de
     * démonstration ne connaît que les clients de démonstration.
     */
    @Transactional
    public Account openAccountForCustomer(
            Actor actor, UUID customerId, Currency currency, String label) {
        var customer =
                customerRefs
                        .findByCustomerIdForUpdate(customerId)
                        .filter(found -> access.isCustomerVisible(actor, found))
                        .orElseThrow(CustomerNotFoundException::new);
        return openAccountFor(customer, currency, label);
    }

    private Account openAccountFor(CustomerRef customer, Currency currency, String label) {
        if (customer.isSuspended()) {
            throw new CustomerSuspendedException();
        }
        var active =
                accounts.countByCustomerIdAndStatus(customer.getCustomerId(), AccountStatus.ACTIVE);
        if (active >= Account.MAX_ACTIVE_PER_CUSTOMER) {
            throw new AccountLimitReachedException();
        }
        return insertWithFreeNumber(customer.getCustomerId(), currency, label);
    }

    @Transactional(readOnly = true)
    public Page<Account> listAccounts(Actor actor, UUID customerFilter, int page, int size) {
        var pageable = PageRequest.of(page, size, NEWEST_FIRST);
        var scope = access.createScope(actor, customerFilter);
        if (scope.empty()) {
            return Page.empty(pageable);
        }
        return accounts.findAll(
                (root, query, builder) ->
                        scope.createPredicate(builder, query, root.get("customerId")),
                pageable);
    }

    /** Vue d'ensemble de la banque : des totaux globaux, sans donnée personnelle. */
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Transactional(readOnly = true)
    public AccountSummary getSummary() {
        var totals =
                accounts.sumOpenBalancesByCurrency().stream()
                        .map(total ->
                                new AccountSummary.CurrencyTotal(
                                        total.getCurrency(),
                                        total.getBalance(),
                                        total.getOpenAccounts()))
                        .toList();
        var active = customerRefs.countByStatus(CustomerStatus.ACTIVE);
        var suspended = customerRefs.countByStatus(CustomerStatus.SUSPENDED);
        return new AccountSummary(
                customerRefs.count(),
                active,
                suspended,
                accounts.countByStatus(AccountStatus.ACTIVE),
                accounts.countByStatus(AccountStatus.CLOSED),
                totals);
    }

    @Transactional(readOnly = true)
    public Account getAccount(Actor actor, UUID accountId) {
        var account = accounts.findById(accountId).orElseThrow(AccountNotFoundException::new);
        access.requireVisible(actor, account);
        return account;
    }

    /** Clôture un compte de solde nul. Un client suspendu ne peut plus rien modifier. */
    @Transactional
    public Account closeAccount(Actor actor, UUID accountId) {
        // Le verrou sérialise deux clôtures simultanées : la seconde voit le compte déjà clôturé
        var account =
                accounts.findByIdForUpdate(accountId).orElseThrow(AccountNotFoundException::new);
        access.requireVisible(actor, account);
        if (!actor.isAdmin()
                && customerRefs
                        .findById(account.getCustomerId())
                        .map(CustomerRef::isSuspended)
                        .orElse(false)) {
            throw new CustomerSuspendedException();
        }
        account.close(clock.instant());
        return accounts.saveAndFlush(account);
    }

    /**
     * Insère le compte avec un numéro libre. Chaque essai insère réellement : une collision sur la
     * contrainte unique est absorbée par la base (sans abîmer la transaction) et déclenche un
     * nouveau numéro, ce qui est quasi impossible sur 11 chiffres aléatoires.
     */
    private Account insertWithFreeNumber(UUID customerId, Currency currency, String label) {
        for (int attempt = 0; attempt < MAX_NUMBER_ATTEMPTS; attempt++) {
            var account =
                    Account.open(
                            customerId,
                            numberGenerator.generateIban(),
                            currency,
                            label,
                            clock.instant());
            if (accounts.insertIfNumberFree(account)) {
                return account;
            }
        }
        throw new AccountNumberUnavailableException();
    }
}
