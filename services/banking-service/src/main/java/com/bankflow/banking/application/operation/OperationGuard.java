package com.bankflow.banking.application.operation;

import com.bankflow.banking.application.account.AccountAccess;
import com.bankflow.banking.application.account.AccountNotFoundException;
import com.bankflow.banking.application.account.CustomerSuspendedException;
import com.bankflow.banking.domain.account.Account;
import com.bankflow.banking.domain.account.AccountClosedException;
import com.bankflow.banking.domain.account.AccountRepository;
import com.bankflow.banking.domain.customer.CustomerRef;
import com.bankflow.banking.domain.customer.CustomerRefRepository;
import com.bankflow.banking.domain.transaction.Actor;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Prépare un compte à recevoir une opération : verrou, visibilité, client non suspendu, compte
 * ouvert. À appeler dans une transaction ; le verrou dure jusqu'à son terme.
 */
@Component
public class OperationGuard {

    private final AccountRepository accounts;
    private final CustomerRefRepository customerRefs;
    private final AccountAccess access;

    OperationGuard(
            AccountRepository accounts, CustomerRefRepository customerRefs, AccountAccess access) {
        this.accounts = accounts;
        this.customerRefs = customerRefs;
        this.access = access;
    }

    /** Verrouille le compte (les opérations d'un même compte sont ainsi sérialisées). */
    public Account lockUsableAccount(Actor actor, UUID accountId) {
        var account =
                accounts.findByIdForUpdate(accountId).orElseThrow(AccountNotFoundException::new);
        access.requireVisible(actor, account);
        var owner = customerRefs.findById(account.getCustomerId());
        if (owner.map(CustomerRef::isSuspended).orElse(false)) {
            throw new CustomerSuspendedException();
        }
        if (account.isClosed()) {
            throw new AccountClosedException();
        }
        return account;
    }
}
