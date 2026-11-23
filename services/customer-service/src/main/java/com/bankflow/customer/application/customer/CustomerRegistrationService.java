package com.bankflow.customer.application.customer;

import com.bankflow.customer.domain.customer.Customer;
import com.bankflow.customer.domain.customer.CustomerCreated;
import com.bankflow.customer.domain.customer.CustomerRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crée le client d'un utilisateur inscrit. L'Auth Service rejoue {@code UserRegistered} à chaque
 * démarrage : le traitement est donc idempotent sur {@code userId}.
 */
@Service
public class CustomerRegistrationService {

    private final CustomerRepository customers;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    CustomerRegistrationService(
            CustomerRepository customers, ApplicationEventPublisher events, Clock clock) {
        this.customers = customers;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Crée le client s'il n'existe pas ; sinon passe en démonstration si l'événement le demande
     * (jamais l'inverse). Dans tous les cas {@code CustomerCreated} est publié avec l'état actuel,
     * pour que le Banking Service rattrape un événement perdu.
     *
     * @throws org.springframework.dao.DataIntegrityViolationException si un message concurrent a
     *     créé le client entre-temps : l'appelant rejoue alors l'appel, qui trouvera le client
     */
    @Transactional
    public void registerUser(UUID userId, String email, boolean demo) {
        var now = clock.instant();
        var customer =
                customers
                        .findByUserId(userId)
                        .orElseGet(() ->
                                customers.saveAndFlush(
                                        Customer.createFromUser(userId, email, demo, now)));
        if (demo) {
            customer.markAsDemo(now);
        }
        events.publishEvent(CustomerCreated.of(customer, now));
    }
}
