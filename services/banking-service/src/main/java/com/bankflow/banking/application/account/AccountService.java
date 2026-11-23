package com.bankflow.banking.application.account;

import com.bankflow.banking.domain.account.Account;
import com.bankflow.banking.domain.account.AccountNumberGenerator;
import com.bankflow.banking.domain.account.AccountRepository;
import com.bankflow.banking.domain.account.AccountStatus;
import com.bankflow.banking.domain.customer.CustomerRef;
import com.bankflow.banking.domain.customer.CustomerRefRepository;
import com.bankflow.banking.domain.money.Currency;
import java.time.Clock;
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

    /** Ouvre un compte pour l'appelant lui-même, même s'il est administrateur. */
    @Transactional
    public Account openAccount(UUID userId, Currency currency, String label) {
        var customer =
                customerRefs
                        .findByUserIdForUpdate(userId)
                        .orElseThrow(CustomerNotReadyException::new);
        if (customer.isSuspended()) {
            throw new CustomerSuspendedException();
        }
        var active =
                accounts.countByCustomerIdAndStatus(customer.getCustomerId(), AccountStatus.ACTIVE);
        if (active >= Account.MAX_ACTIVE_PER_CUSTOMER) {
            throw new AccountLimitReachedException();
        }
        var account =
                Account.open(
                        customer.getCustomerId(),
                        generateFreeAccountNumber(),
                        currency,
                        label,
                        clock.instant());
        return accounts.saveAndFlush(account);
    }

    @Transactional(readOnly = true)
    public Page<Account> listAccounts(UUID userId, boolean admin, int page, int size) {
        var pageable = PageRequest.of(page, size, NEWEST_FIRST);
        if (admin) {
            return accounts.findAll(pageable);
        }
        return customerRefs
                .findByUserId(userId)
                .map(customer -> accounts.findByCustomerId(customer.getCustomerId(), pageable))
                .orElseGet(() -> Page.empty(pageable));
    }

    @Transactional(readOnly = true)
    public Account getAccount(UUID userId, boolean admin, UUID accountId) {
        return findVisibleAccount(userId, admin, accountId);
    }

    /** Clôture un compte de solde nul. Un client suspendu ne peut plus rien modifier. */
    @Transactional
    public Account closeAccount(UUID userId, boolean admin, UUID accountId) {
        var account = findVisibleAccount(userId, admin, accountId);
        if (!admin
                && customerRefs.findByUserId(userId).map(CustomerRef::isSuspended).orElse(false)) {
            throw new CustomerSuspendedException();
        }
        account.close(clock.instant());
        return accounts.saveAndFlush(account);
    }

    private Account findVisibleAccount(UUID userId, boolean admin, UUID accountId) {
        var account = accounts.findById(accountId).orElseThrow(AccountNotFoundException::new);
        if (admin) {
            return account;
        }
        var ownsAccount =
                customerRefs
                        .findByUserId(userId)
                        .map(customer -> customer.getCustomerId().equals(account.getCustomerId()))
                        .orElse(false);
        if (!ownsAccount) {
            throw new AccountNotFoundException();
        }
        return account;
    }

    /** Régénère le numéro en cas de collision, ce qui est quasi impossible sur 11 chiffres. */
    private String generateFreeAccountNumber() {
        for (int attempt = 1; attempt < MAX_NUMBER_ATTEMPTS; attempt++) {
            var candidate = numberGenerator.generateIban();
            if (!accounts.existsByAccountNumber(candidate)) {
                return candidate;
            }
        }
        var last = numberGenerator.generateIban();
        if (accounts.existsByAccountNumber(last)) {
            throw new IllegalStateException(
                    "Aucun numéro de compte libre après " + MAX_NUMBER_ATTEMPTS + " essais.");
        }
        return last;
    }
}
