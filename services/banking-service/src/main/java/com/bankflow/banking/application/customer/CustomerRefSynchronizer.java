package com.bankflow.banking.application.customer;

import com.bankflow.banking.domain.customer.CustomerRef;
import com.bankflow.banking.domain.customer.CustomerRefRepository;
import com.bankflow.banking.domain.customer.CustomerStatus;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tient la projection locale des clients à jour. Idempotent sur {@code customerId} et insensible au
 * désordre : un événement antérieur à l'état déjà appliqué est ignoré.
 */
@Service
public class CustomerRefSynchronizer {

    private static final Logger log = LoggerFactory.getLogger(CustomerRefSynchronizer.class);

    private final CustomerRefRepository customerRefs;

    CustomerRefSynchronizer(CustomerRefRepository customerRefs) {
        this.customerRefs = customerRefs;
    }

    /**
     * Crée la référence si elle manque, sinon la remplace par l'état reçu s'il n'est pas périmé.
     */
    @Transactional
    public void syncCustomer(
            UUID customerId, UUID userId, String email, CustomerStatus status, Instant occurredAt) {
        customerRefs
                .findById(customerId)
                .ifPresentOrElse(
                        existing -> {
                            if (existing.isStaleEvent(occurredAt)) {
                                log.debug("Événement client périmé ignoré");
                                return;
                            }
                            existing.applyState(userId, email, status, occurredAt);
                        },
                        () ->
                                customerRefs.saveAndFlush(
                                        new CustomerRef(
                                                customerId, userId, email, status, occurredAt)));
    }
}
