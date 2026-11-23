package com.bankflow.customer.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.bankflow.customer.CustomerApiTestSupport;
import com.bankflow.customer.domain.customer.CustomerStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

class UserRegisteredConsumerIntegrationTests extends CustomerApiTestSupport {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired private UserRegisteredConsumer consumer;

    @Test
    void unUserRegisteredCreeLeClientEtPublieCustomerCreated() throws Exception {
        var userId = UUID.randomUUID();
        var email = generateUniqueEmail();

        sendUserRegistered(userId, "  " + email.toUpperCase() + " ", null);

        var customer = awaitCustomer(userId);
        assertThat(customer.getEmail()).isEqualTo(email);
        assertThat(customer.getStatus()).isEqualTo(CustomerStatus.ACTIVE);
        assertThat(customer.isDemo()).isFalse();
        var events =
                awaitEvents("bank.customers", customer.getId().toString(), "CustomerCreated", 1);
        var envelope = events.get(0);
        assertThat(envelope.get("eventId").asString()).isNotBlank();
        assertThat(envelope.get("eventVersion").asInt()).isEqualTo(1);
        assertThat(envelope.get("occurredAt").asString()).isNotBlank();
        assertThat(envelope.get("producer").asString()).isEqualTo("customer-service");
        var payload = envelope.get("payload");
        assertThat(payload.get("customerId").asString()).isEqualTo(customer.getId().toString());
        assertThat(payload.get("userId").asString()).isEqualTo(userId.toString());
        assertThat(payload.get("email").asString()).isEqualTo(email);
        assertThat(payload.get("status").asString()).isEqualTo("ACTIVE");
    }

    @Test
    void lAbsenceDuChampDemoDonneUnClientOrdinaire() throws Exception {
        var userId = UUID.randomUUID();

        sendUserRegistered(userId, generateUniqueEmail(), null);

        assertThat(awaitCustomer(userId).isDemo()).isFalse();
    }

    @Test
    void demoVraiCreeUnClientDeDemonstration() throws Exception {
        var userId = UUID.randomUUID();

        sendUserRegistered(userId, generateUniqueEmail(), true);

        assertThat(awaitCustomer(userId).isDemo()).isTrue();
    }

    @Test
    void lePremierMessageRejoueNeCreePasDeDoublonMaisRepublieCustomerCreated() throws Exception {
        var userId = UUID.randomUUID();
        var email = generateUniqueEmail();
        sendUserRegistered(userId, email, null);
        var customer = awaitCustomer(userId);

        sendUserRegistered(userId, email, null);

        var events =
                awaitEvents("bank.customers", customer.getId().toString(), "CustomerCreated", 2);
        assertThat(events).hasSize(2);
        assertThat(customerRepository.findAll().stream().filter(c -> c.getUserId().equals(userId)))
                .hasSize(1);
    }

    @Test
    void demoVraiSurUnClientExistantLePasseEnDemonstrationMaisJamaisLInverse() throws Exception {
        var userId = UUID.randomUUID();
        var email = generateUniqueEmail();
        sendUserRegistered(userId, email, null);
        var customer = awaitCustomer(userId);
        assertThat(customer.isDemo()).isFalse();

        sendUserRegistered(userId, email, true);
        awaitEvents("bank.customers", customer.getId().toString(), "CustomerCreated", 2);
        assertThat(customerRepository.findByUserId(userId).orElseThrow().isDemo()).isTrue();

        sendUserRegistered(userId, email, false);
        awaitEvents("bank.customers", customer.getId().toString(), "CustomerCreated", 3);
        assertThat(customerRepository.findByUserId(userId).orElseThrow().isDemo()).isTrue();
    }

    @Test
    void deuxMessagesConcurrentsPourLeMemeUtilisateurDonnentUnSeulClient() throws Exception {
        var userId = UUID.randomUUID();
        var email = generateUniqueEmail();
        var value = buildEnvelope("UserRegistered", 1, userId, email, null);
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(4);
        try {
            var tasks = new ArrayList<Future<?>>();
            for (var i = 0; i < 4; i++) {
                tasks.add(
                        pool.submit(() -> {
                            start.await();
                            consumer.consume(
                                    new ConsumerRecord<>(
                                            "bank.users", 0, 0L, userId.toString(), value));
                            return null;
                        }));
            }
            start.countDown();
            for (var task : tasks) {
                task.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(customerRepository.findAll().stream().filter(c -> c.getUserId().equals(userId)))
                .hasSize(1);
        var customerId = customerRepository.findByUserId(userId).orElseThrow().getId();
        assertThat(awaitEvents("bank.customers", customerId.toString(), "CustomerCreated", 1))
                .isNotEmpty();
    }

    @Test
    void unMessageIllisibleEstIgnoreEtLeSuivantEstTraite() throws Exception {
        var userId = UUID.randomUUID();
        kafkaTemplate.send("bank.users", "cle-illisible", "ceci n'est pas du JSON").get();
        kafkaTemplate.send("bank.users", "cle-vide", "{\"eventType\":\"UserRegistered\"}").get();
        kafkaTemplate
                .send(
                        "bank.users",
                        "cle-version",
                        buildEnvelope("UserRegistered", 2, userId, generateUniqueEmail(), null))
                .get();
        kafkaTemplate
                .send(
                        "bank.users",
                        "cle-type",
                        buildEnvelope("AutreEvenement", 1, userId, generateUniqueEmail(), null))
                .get();

        sendUserRegistered(userId, generateUniqueEmail(), null);

        assertThat(awaitCustomer(userId).getUserId()).isEqualTo(userId);
    }

    private void sendUserRegistered(UUID userId, String email, Boolean demo) throws Exception {
        kafkaTemplate
                .send(
                        "bank.users",
                        userId.toString(),
                        buildEnvelope("UserRegistered", 1, userId, email, demo))
                .get();
    }

    private static String buildEnvelope(
            String eventType, int version, UUID userId, String email, Boolean demo) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("userId", userId);
        payload.put("email", email);
        if (demo != null) {
            payload.put("demo", demo);
        }
        var envelope = new LinkedHashMap<String, Object>();
        envelope.put("eventId", UUID.randomUUID());
        envelope.put("eventType", eventType);
        envelope.put("eventVersion", version);
        envelope.put("occurredAt", Instant.now());
        envelope.put("producer", "auth-service");
        envelope.put("payload", payload);
        return JSON.writeValueAsString(envelope);
    }
}
