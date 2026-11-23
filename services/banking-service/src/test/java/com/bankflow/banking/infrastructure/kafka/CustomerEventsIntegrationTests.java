package com.bankflow.banking.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.bankflow.banking.BankingApiTestSupport;
import com.bankflow.banking.domain.customer.CustomerStatus;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

class CustomerEventsIntegrationTests extends BankingApiTestSupport {

    private static final String TOPIC = "bank.customers";
    private static final Instant T1 = Instant.parse("2026-11-23T10:00:00Z");
    private static final Instant T2 = T1.plus(5, ChronoUnit.MINUTES);
    private static final Instant T3 = T2.plus(5, ChronoUnit.MINUTES);

    @Autowired private KafkaTemplate<String, String> kafka;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void unCustomerCreatedCreeLaReference() throws Exception {
        var customerId = UUID.randomUUID();
        var userId = UUID.randomUUID();

        publish(customerId, "CustomerCreated", 1, userId, "a@bankflow.test", "ACTIVE", T1);

        awaitTrue(() -> countRefs(customerId) == 1);
        var ref = customerRefs.findById(customerId).orElseThrow();
        assertThat(ref.getUserId()).isEqualTo(userId);
        assertThat(ref.getEmail()).isEqualTo("a@bankflow.test");
        assertThat(ref.getStatus()).isEqualTo(CustomerStatus.ACTIVE);
        assertThat(ref.getUpdatedAt()).isEqualTo(T1);
    }

    @Test
    void unMemeEvenementRecuDeuxFoisNeCreeQuUneLigneEtLaisseLEtatInchange() throws Exception {
        var customerId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        publish(customerId, "CustomerCreated", 1, userId, "b@bankflow.test", "ACTIVE", T1);
        publish(customerId, "CustomerCreated", 1, userId, "b@bankflow.test", "ACTIVE", T1);
        awaitMarkerProcessed();

        assertThat(countRefs(customerId)).isEqualTo(1);
        var ref = customerRefs.findById(customerId).orElseThrow();
        assertThat(ref.getStatus()).isEqualTo(CustomerStatus.ACTIVE);
        assertThat(ref.getUpdatedAt()).isEqualTo(T1);
    }

    @Test
    void unCustomerUpdatedSuspendedMetLeStatutAJour() throws Exception {
        var customerId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        publish(customerId, "CustomerCreated", 1, userId, "c@bankflow.test", "ACTIVE", T1);
        awaitTrue(() -> countRefs(customerId) == 1);

        publish(customerId, "CustomerUpdated", 1, userId, "c2@bankflow.test", "SUSPENDED", T2);

        awaitTrue(() ->
                customerRefs
                        .findById(customerId)
                        .map(ref -> ref.getStatus() == CustomerStatus.SUSPENDED)
                        .orElse(false));
        var ref = customerRefs.findById(customerId).orElseThrow();
        assertThat(ref.getEmail()).isEqualTo("c2@bankflow.test");
        assertThat(ref.getUpdatedAt()).isEqualTo(T2);
    }

    @Test
    void unAncienCustomerCreatedNeReactivePasUnClientSuspendu() throws Exception {
        var customerId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        publish(customerId, "CustomerUpdated", 1, userId, "e@bankflow.test", "SUSPENDED", T2);
        awaitTrue(() -> countRefs(customerId) == 1);

        publish(customerId, "CustomerCreated", 1, userId, "e@bankflow.test", "ACTIVE", T1);
        awaitMarkerProcessed();

        var ref = customerRefs.findById(customerId).orElseThrow();
        assertThat(ref.getStatus()).isEqualTo(CustomerStatus.SUSPENDED);
        assertThat(ref.getUpdatedAt()).isEqualTo(T2);
    }

    @Test
    void unEvenementPlusRecentEstApplique() throws Exception {
        var customerId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        publish(customerId, "CustomerUpdated", 1, userId, "f@bankflow.test", "SUSPENDED", T2);
        awaitTrue(() -> countRefs(customerId) == 1);

        publish(customerId, "CustomerUpdated", 1, userId, "f@bankflow.test", "ACTIVE", T3);

        awaitTrue(() ->
                customerRefs
                        .findById(customerId)
                        .map(ref -> ref.getStatus() == CustomerStatus.ACTIVE)
                        .orElse(false));
        assertThat(customerRefs.findById(customerId).orElseThrow().getUpdatedAt()).isEqualTo(T3);
    }

    @Test
    void lesMessagesInvalidesSontIgnoresSansBloquerLaPartition() throws Exception {
        var key = UUID.randomUUID().toString();
        var statusInconnu = UUID.randomUUID();
        var versionDeux = UUID.randomUUID();
        var sansDate = UUID.randomUUID();
        var dateInvalide = UUID.randomUUID();
        kafka.send(TOPIC, key, "ceci n'est pas du JSON").get();
        kafka.send(TOPIC, key, "{\"eventType\":\"Inconnu\",\"eventVersion\":1,\"payload\":{}}")
                .get();
        publish(statusInconnu, "CustomerCreated", 1, UUID.randomUUID(), "g@b.test", "VIP", T1);
        publishRaw(
                "{\"eventType\":\"CustomerCreated\",\"eventVersion\":1,\"occurredAt\":\"%s\","
                                .formatted(T1)
                        + "\"payload\":{\"customerId\":\"pas-un-uuid\",\"userId\":\"%s\","
                                .formatted(UUID.randomUUID())
                        + "\"email\":\"h@b.test\",\"status\":\"ACTIVE\"}}");
        publish(versionDeux, "CustomerCreated", 2, UUID.randomUUID(), "i@b.test", "ACTIVE", T1);
        publishRaw(
                "{\"eventType\":\"CustomerCreated\",\"eventVersion\":1,"
                        + "\"payload\":{\"customerId\":\"%s\",\"userId\":\"%s\","
                                .formatted(sansDate, UUID.randomUUID())
                        + "\"email\":\"j@b.test\",\"status\":\"ACTIVE\"}}");
        publishRaw(
                "{\"eventType\":\"CustomerCreated\",\"eventVersion\":1,"
                        + "\"occurredAt\":\"hier\",\"payload\":{\"customerId\":\"%s\","
                                .formatted(dateInvalide)
                        + "\"userId\":\"%s\",\"email\":\"k@b.test\",\"status\":\"ACTIVE\"}}"
                                .formatted(UUID.randomUUID()));
        awaitMarkerProcessed();

        for (var ignored : new UUID[] {statusInconnu, versionDeux, sansDate, dateInvalide}) {
            assertThat(countRefs(ignored)).as(ignored.toString()).isZero();
        }
    }

    @Test
    void uneErreurDeBaseNEmpecheNiLeMessageSuivantNiLaPartition() throws Exception {
        var userId = UUID.randomUUID();
        var first = UUID.randomUUID();
        publish(first, "CustomerCreated", 1, userId, "l@bankflow.test", "ACTIVE", T1);
        awaitTrue(() -> countRefs(first) == 1);
        var conflicting = UUID.randomUUID();

        // Le userId est déjà pris par un autre client : l'insertion échoue (contrainte unique)
        publish(conflicting, "CustomerCreated", 1, userId, "l2@bankflow.test", "ACTIVE", T1);
        awaitMarkerProcessed();

        assertThat(countRefs(conflicting)).isZero();
        assertThat(countRefs(first)).isEqualTo(1);
    }

    /** Marqueur : quand il est traité, les messages précédents (même partition) le sont aussi. */
    private void awaitMarkerProcessed() throws Exception {
        var markerId = UUID.randomUUID();
        publish(markerId, "CustomerCreated", 1, UUID.randomUUID(), "m@bankflow.test", "ACTIVE", T1);
        awaitTrue(() -> countRefs(markerId) == 1);
    }

    private void publish(
            UUID customerId,
            String eventType,
            int version,
            UUID userId,
            String email,
            String status,
            Instant occurredAt)
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
                                occurredAt,
                                customerId,
                                userId,
                                email,
                                status);
        publishRaw(envelope);
    }

    /** Toujours la même clé : un seul topic partitionné par clé garde l'ordre d'envoi. */
    private void publishRaw(String envelope) throws Exception {
        kafka.send(TOPIC, "tests", envelope).get();
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
