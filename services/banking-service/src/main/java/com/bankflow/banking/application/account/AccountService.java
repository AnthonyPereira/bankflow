package com.bankflow.banking.application.account;

import com.bankflow.banking.domain.account.Account;
import com.bankflow.banking.domain.account.AccountNumberGenerator;
import com.bankflow.banking.domain.account.AccountRepository;
import com.bankflow.banking.domain.account.AccountStatus;
import com.bankflow.banking.domain.customer.CustomerRef;
import com.bankflow.banking.domain.customer.CustomerRefRepository;
import com.bankflow.banking.domain.customer.CustomerStatus;
import com.bankflow.banking.domain.money.Currency;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cas d'usage des comptes. Un client n'accède qu'aux siens (un compte d'autrui est « introuvable
 * »), un administrateur accède à tous.
 */
@Service
public class AccountService {

    private static final int MAX_NUMBER_ATTEMPTS = 3;
    private static final Sort NEWEST_FIRST =
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final AccountRepository accounts;
    private final CustomerRefRepository customerRefs;
    private final AccountNumberGenerator numberGenerator;
    private final Clock clock;

    AccountService(
            AccountRepository accounts,
            CustomerRefRepository customerRefs,
            AccountNumberGenerator numberGenerator,
            Clock clock) {
        this.accounts = accounts;
        this.customerRefs = customerRefs;
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

    /** Ouvre un compte au nom d'un client, à la demande d'un conseiller. */
    @Transactional
    public Account openAccountForCustomer(UUID customerId, Currency currency, String label) {
        var customer =
                customerRefs
                        .findByCustomerIdForUpdate(customerId)
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
    public Page<Account> listAccounts(
            UUID userId, boolean admin, UUID customerFilter, int page, int size) {
        var pageable = PageRequest.of(page, size, NEWEST_FIRST);
        if (admin) {
            return customerFilter == null
                    ? accounts.findAll(pageable)
                    : accounts.findByCustomerId(customerFilter, pageable);
        }
        return customerRefs
                .findByUserId(userId)
                .map(customer -> accounts.findByCustomerId(customer.getCustomerId(), pageable))
                .orElseGet(() -> Page.empty(pageable));
    }

    /** Vue d'ensemble de la banque, réservée aux administrateurs (contrôlé par l'API). */
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
    public Account getAccount(UUID userId, boolean admin, UUID accountId) {
        return findVisibleAccount(userId, admin, accountId);
    }

    /** Clôture un compte de solde nul. Un client suspendu ne peut plus rien modifier. */
    @Transactional
    public Account closeAccount(UUID userId, boolean admin, UUID accountId) {
        // Le verrou sérialise deux clôtures simultanées : la seconde voit le compte déjà clôturé
        var account =
                accounts.findByIdForUpdate(accountId).orElseThrow(AccountNotFoundException::new);
        var customer = customerRefs.findByUserId(userId);
        requireVisible(account, admin, customer);
        if (!admin && customer.map(CustomerRef::isSuspended).orElse(false)) {
            throw new CustomerSuspendedException();
        }
        account.close(clock.instant());
        return accounts.saveAndFlush(account);
    }

    private Account findVisibleAccount(UUID userId, boolean admin, UUID accountId) {
        var account = accounts.findById(accountId).orElseThrow(AccountNotFoundException::new);
        requireVisible(account, admin, customerRefs.findByUserId(userId));
        return account;
    }

    private static void requireVisible(
            Account account, boolean admin, Optional<CustomerRef> customer) {
        var ownsAccount =
                customer.map(ref -> ref.getCustomerId().equals(account.getCustomerId()))
                        .orElse(false);
        if (!admin && !ownsAccount) {
            throw new AccountNotFoundException();
        }
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
