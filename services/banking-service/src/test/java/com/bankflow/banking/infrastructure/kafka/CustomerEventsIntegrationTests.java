package com.bankflow.banking.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.bankflow.banking.BankingApiTestSupport;
import com.bankflow.banking.domain.customer.CustomerStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

class CustomerEventsIntegrationTests extends BankingApiTestSupport {

    private static final String TOPIC = "bank.customers";

    @Autowired private KafkaTemplate<String, String> kafka;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void unCustomerCreatedCreeLaReference() throws Exception {
        var customerId = UUID.randomUUID();
        var userId = UUID.randomUUID();

        publish(customerId, "CustomerCreated", 1, customerId, userId, "a@bankflow.test", "ACTIVE");

        awaitTrue(() -> countRefs(customerId) == 1);
        var ref = customerRefs.findById(customerId).orElseThrow();
        assertThat(ref.getUserId()).isEqualTo(userId);
        assertThat(ref.getEmail()).isEqualTo("a@bankflow.test");
        assertThat(ref.getStatus()).isEqualTo(CustomerStatus.ACTIVE);
    }

    @Test
    void unMemeEvenementRecuDeuxFoisNeCreeQuUneLigne() throws Exception {
        var customerId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        publish(customerId, "CustomerCreated", 1, customerId, userId, "b@bankflow.test", "ACTIVE");
        publish(customerId, "CustomerCreated", 1, customerId, userId, "b@bankflow.test", "ACTIVE");
        // Marqueur : quand il est traité, les deux messages précédents (même partition) le sont
        // aussi
        var markerId = UUID.randomUUID();
        publish(
                markerId,
                "CustomerCreated",
                1,
                markerId,
                UUID.randomUUID(),
                "m@bankflow.test",
                "ACTIVE");

        awaitTrue(() -> countRefs(markerId) == 1);
        assertThat(countRefs(customerId)).isEqualTo(1);
    }

    @Test
    void unCustomerUpdatedSuspendedMetLeStatutAJour() throws Exception {
        var customerId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        publish(customerId, "CustomerCreated", 1, customerId, userId, "c@bankflow.test", "ACTIVE");
        awaitTrue(() -> countRefs(customerId) == 1);

        publish(
                customerId,
                "CustomerUpdated",
                1,
                customerId,
                userId,
                "c2@bankflow.test",
                "SUSPENDED");

        awaitTrue(() ->
                customerRefs
                        .findById(customerId)
                        .map(ref -> ref.getStatus() == CustomerStatus.SUSPENDED)
                        .orElse(false));
        assertThat(customerRefs.findById(customerId).orElseThrow().getEmail())
                .isEqualTo("c2@bankflow.test");
    }

    @Test
    void unMessageIllisibleEstIgnoreEtLeSuivantEstTraite() throws Exception {
        var customerId = UUID.randomUUID();
        var key = customerId.toString();
        kafka.send(TOPIC, key, "ceci n'est pas du JSON").get();
        kafka.send(TOPIC, key, "{\"eventType\":\"Inconnu\",\"eventVersion\":1,\"payload\":{}}")
                .get();
        publish(
                customerId,
                "CustomerCreated",
                2,
                customerId,
                UUID.randomUUID(),
                "d@bankflow.test",
                "ACTIVE");
        publish(
                customerId,
                "CustomerCreated",
                1,
                customerId,
                UUID.randomUUID(),
                "d@bankflow.test",
                "VIP");
        publish(
                customerId,
                "CustomerCreated",
                1,
                customerId,
                UUID.randomUUID(),
                "d@bankflow.test",
                "ACTIVE");

        awaitTrue(() -> countRefs(customerId) == 1);
    }

    private void publish(
            UUID key,
            String eventType,
            int version,
            UUID customerId,
            UUID userId,
            String email,
            String status)
            throws Exception {
        var envelope =
                """
                {"eventId":"%s","eventType":"%s","eventVersion":%d,"occurredAt":"%s",\
                "producer":"customer-service","payload":{"customerId":"%s","userId":"%s",\
                "email":"%s","status":"%s"}}\
                """
                        .formatted(
                                UUID.randomUUID(),
                                eventType,
                                version,
                                Instant.now(),
                                customerId,
                                userId,
                                email,
                                status);
        kafka.send(TOPIC, key.toString(), envelope).get();
    }

    private int countRefs(UUID customerId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM customer_refs WHERE customer_id = ?::uuid",
                Integer.class,
                customerId.toString());
    }

    /** Attente bornée : la consommation est asynchrone. */
    private static void awaitTrue(BooleanSupplier condition) throws InterruptedException {
        var deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Condition non remplie dans le délai imparti");
    }
}
