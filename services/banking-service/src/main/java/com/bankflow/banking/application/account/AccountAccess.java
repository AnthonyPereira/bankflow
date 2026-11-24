package com.bankflow.banking.application.account;

import com.bankflow.banking.application.account.exception.AccountNotFoundException;
import com.bankflow.banking.domain.account.Account;
import com.bankflow.banking.domain.customer.CustomerRef;
import com.bankflow.banking.domain.customer.CustomerRefRepository;
import com.bankflow.banking.domain.transaction.Actor;
import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Règle de visibilité unique du service : un client voit ses comptes, le vrai administrateur voit
 * tout, un administrateur de démonstration ne voit que les comptes des clients de démonstration. Un
 * compte invisible est « introuvable ».
 */
@Component
public class AccountAccess {

    private final CustomerRefRepository customerRefs;

    AccountAccess(CustomerRefRepository customerRefs) {
        this.customerRefs = customerRefs;
    }

    /** Étendue des comptes visibles pour les listes, traduite en condition SQL. */
    public record Scope(UUID customerId, boolean demoOnly, boolean empty) {

        /** Vrai si l'étendue restreint les comptes (faux : le vrai administrateur voit tout). */
        public boolean hasFilter() {
            return customerId != null || demoOnly;
        }

        /** Condition sur le client propriétaire, à appliquer à {@code customerIdPath}. */
        public Predicate createPredicate(
                CriteriaBuilder builder, AbstractQuery<?> query, Path<UUID> customerIdPath) {
            var predicates = new ArrayList<Predicate>();
            if (customerId != null) {
                predicates.add(builder.equal(customerIdPath, customerId));
            }
            if (demoOnly) {
                var demoCustomers = query.subquery(UUID.class);
                var customer = demoCustomers.from(CustomerRef.class);
                demoCustomers
                        .select(customer.get("customerId"))
                        .where(builder.isTrue(customer.get("demo")));
                predicates.add(customerIdPath.in(demoCustomers));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        }
    }

    public boolean isVisible(Actor actor, Account account) {
        if (actor.isAdmin()) {
            return !actor.isRestrictedToDemo()
                    || customerRefs
                            .findById(account.getCustomerId())
                            .map(CustomerRef::isDemo)
                            .orElse(false);
        }
        return customerRefs
                .findByUserId(actor.userId())
                .map(ref -> ref.getCustomerId().equals(account.getCustomerId()))
                .orElse(false);
    }

    public void requireVisible(Actor actor, Account account) {
        if (!isVisible(actor, account)) {
            throw new AccountNotFoundException();
        }
    }

    /**
     * Un conseiller agit au nom d'un client : un administrateur de démonstration, seulement démo.
     */
    public boolean isCustomerVisible(Actor actor, CustomerRef customer) {
        return !actor.isRestrictedToDemo() || customer.isDemo();
    }

    /**
     * Étendue d'une liste. {@code customerFilter} n'est pris en compte que pour un administrateur ;
     * un client sans fiche ne voit rien ({@code empty}).
     */
    public Scope createScope(Actor actor, UUID customerFilter) {
        if (actor.isAdmin()) {
            return new Scope(customerFilter, actor.isRestrictedToDemo(), false);
        }
        return customerRefs
                .findByUserId(actor.userId())
                .map(customer -> new Scope(customer.getCustomerId(), false, false))
                .orElseGet(() -> new Scope(null, false, true));
    }
}
