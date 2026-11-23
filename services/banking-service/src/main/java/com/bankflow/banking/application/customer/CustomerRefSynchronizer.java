package com.bankflow.banking.application.customer;

import com.bankflow.banking.domain.customer.CustomerRef;
import com.bankflow.banking.domain.customer.CustomerRefRepository;
import com.bankflow.banking.domain.customer.CustomerStatus;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tient la projection locale des clients à jour. Idempotent sur {@code customerId}. */
@Service
public class CustomerRefSynchronizer {

    private final CustomerRefRepository customerRefs;
    private final Clock clock;

    CustomerRefSynchronizer(CustomerRefRepository customerRefs, Clock clock) {
        this.customerRefs = customerRefs;
        this.clock = clock;
    }

    /** Crée la référence si elle manque, sinon la remplace par l'état reçu. */
    @Transactional
    public void syncCustomer(UUID customerId, UUID userId, String email, CustomerStatus status) {
        var now = clock.instant();
        customerRefs
                .findById(customerId)
                .ifPresentOrElse(
                        existing -> existing.applyState(userId, email, status, now),
                        () ->
                                customerRefs.saveAndFlush(
                                        new CustomerRef(customerId, userId, email, status, now)));
    }
}
