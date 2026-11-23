package com.bankflow.customer.application.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bankflow.customer.CustomerApiTestSupport;
import com.bankflow.customer.domain.customer.CustomerStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Événements publiés sur {@code bank.customers} : contenu, date de l'état et transactions. */
class CustomerEventsIntegrationTests extends CustomerApiTestSupport {

    private static final String TOPIC = "bank.customers";

    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private CustomerAdminService adminService;

    @Test
    void laRepublicationDUnEtatInchangeGardeLeMemeOccurredAtQueEnBase() {
        var customer = createCustomer(generateUniqueEmail(), false);

        registrationService.registerUser(customer.getUserId(), customer.getEmail(), false);

        var key = customer.getId().toString();
        var events = awaitEvents(TOPIC, key, "CustomerCreated", 2);
        var storedUpdatedAt =
                customerRepository.findById(customer.getId()).orElseThrow().getUpdatedAt();
        assertThat(occurredAt(events.get(0))).isEqualTo(storedUpdatedAt);
        assertThat(occurredAt(events.get(1))).isEqualTo(storedUpdatedAt);
    }

    @Test
    void apresUneSuspensionCustomerUpdatedEstPlusRecentQueCustomerCreated() {
        var customer = createCustomer(generateUniqueEmail(), false);

        var suspended = adminService.changeStatus(customer.getId(), CustomerStatus.SUSPENDED);

        var key = customer.getId().toString();
        var created = awaitEvents(TOPIC, key, "CustomerCreated", 1).get(0);
        var updated = awaitEvents(TOPIC, key, "CustomerUpdated", 1).get(0);
        assertThat(occurredAt(updated)).isAfter(occurredAt(created));
        assertThat(occurredAt(updated))
                .isEqualTo(
                        customerRepository
                                .findById(suspended.getId())
                                .orElseThrow()
                                .getUpdatedAt());
    }

    @Test
    void passerUnClientSuspenduEnDemonstrationLeReactiveEtPublieCustomerUpdated() {
        var customer = createCustomer(generateUniqueEmail(), false);
        adminService.changeStatus(customer.getId(), CustomerStatus.SUSPENDED);
        var key = customer.getId().toString();
        awaitEvents(TOPIC, key, "CustomerUpdated", 1);

        registrationService.registerUser(customer.getUserId(), customer.getEmail(), true);

        var stored = customerRepository.findById(customer.getId()).orElseThrow();
        assertThat(stored.isDemo()).isTrue();
        assertThat(stored.getStatus()).isEqualTo(CustomerStatus.ACTIVE);
        var updates = awaitEvents(TOPIC, key, "CustomerUpdated", 2);
        var reactivation = updates.get(1);
        assertThat(reactivation.get("payload").get("status").asString()).isEqualTo("ACTIVE");
        assertThat(occurredAt(reactivation)).isEqualTo(stored.getUpdatedAt());
        var republished = awaitEvents(TOPIC, key, "CustomerCreated", 2).get(1);
        assertThat(republished.get("payload").get("status").asString()).isEqualTo("ACTIVE");
    }

    @Test
    void uneTransactionAnnuleeNePublieAucunEvenement() {
        var userId = UUID.randomUUID();
        var customerId = new UUID[1];

        assertThatThrownBy(() ->
                        new TransactionTemplate(transactionManager)
                                .executeWithoutResult(status -> {
                                    registrationService.registerUser(
                                            userId, generateUniqueEmail(), false);
                                    customerId[0] =
                                            customerRepository
                                                    .findByUserId(userId)
                                                    .orElseThrow()
                                                    .getId();
                                    throw new IllegalStateException("annulation voulue");
                                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(customerRepository.findByUserId(userId)).isEmpty();
        assertThat(
                        collectEventsDuring(
                                TOPIC,
                                customerId[0].toString(),
                                "CustomerCreated",
                                Duration.ofSeconds(2)))
                .isEmpty();
    }

    @Test
    void unVerrouOptimistePerduEstDetecteALEcriture() {
        var customer = createCustomer(generateUniqueEmail(), false);
        // Copie détachée lue avant la modification concurrente
        var stale = customerRepository.findById(customer.getId()).orElseThrow();
        adminService.changeStatus(customer.getId(), CustomerStatus.SUSPENDED);
        stale.updateContact("Alice", null, null, null, Instant.now());

        assertThatThrownBy(() -> customerRepository.saveAndFlush(stale))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    private static Instant occurredAt(tools.jackson.databind.JsonNode envelope) {
        return Instant.parse(envelope.get("occurredAt").asString());
    }
}
