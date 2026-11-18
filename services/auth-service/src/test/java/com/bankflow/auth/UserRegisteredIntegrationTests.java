package com.bankflow.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.kafka.KafkaContainer;

class UserRegisteredIntegrationTests extends AuthApiTestSupport {

    @Autowired private KafkaContainer kafka;

    @Test
    void lInscriptionPublieUserRegisteredSansDonneeSensible() throws Exception {
        var email = uniqueEmail();
        var userId = json(register(email, VALID_PASSWORD)).get("id").asString();

        var record = awaitRecord(userId);

        assertThat(record.key()).isEqualTo(userId);
        assertThat(record.value()).doesNotContain(VALID_PASSWORD, "password", "token");
        var envelope =
                tools.jackson.databind.json.JsonMapper.builder().build().readTree(record.value());
        assertThat(envelope.get("eventId").asString()).isNotBlank();
        assertThat(envelope.get("eventType").asString()).isEqualTo("UserRegistered");
        assertThat(envelope.get("eventVersion").asInt()).isEqualTo(1);
        assertThat(envelope.get("occurredAt").asString()).isNotBlank();
        assertThat(envelope.get("producer").asString()).isEqualTo("auth-service");
        assertThat(envelope.get("payload").get("userId").asString()).isEqualTo(userId);
        assertThat(envelope.get("payload").get("email").asString()).isEqualTo(email);
        assertThat(envelope.get("payload").properties()).hasSize(2);
    }

    @Test
    void uneInscriptionRefuseeNePublieAucunEvenement() throws Exception {
        var email = uniqueEmail();
        var userId = json(register(email, VALID_PASSWORD)).get("id").asString();
        awaitRecord(userId);

        assertThat(register(email, VALID_PASSWORD).statusCode()).isEqualTo(409);

        assertThat(recordsFor(email, Duration.ofSeconds(3))).hasSize(1);
    }

    private ConsumerRecord<String, String> awaitRecord(String userId) {
        try (var consumer = newConsumer()) {
            var deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            while (System.nanoTime() < deadline) {
                for (var record : consumer.poll(Duration.ofMillis(500))) {
                    if (userId.equals(record.key())) {
                        return record;
                    }
                }
            }
        }
        throw new AssertionError("UserRegistered non reçu pour " + userId);
    }

    private List<ConsumerRecord<String, String>> recordsFor(String email, Duration duration) {
        var found = new java.util.ArrayList<ConsumerRecord<String, String>>();
        try (var consumer = newConsumer()) {
            var deadline = System.nanoTime() + duration.toNanos();
            while (System.nanoTime() < deadline) {
                for (var record : consumer.poll(Duration.ofMillis(500))) {
                    if (record.value().contains(email)) {
                        found.add(record);
                    }
                }
            }
        }
        return found;
    }

    private KafkaConsumer<String, String> newConsumer() {
        var consumer =
                new KafkaConsumer<>(
                        Map.<String, Object>of(
                                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                                kafka.getBootstrapServers(),
                                ConsumerConfig.GROUP_ID_CONFIG,
                                "test-" + UUID.randomUUID(),
                                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                                "earliest"),
                        new StringDeserializer(),
                        new StringDeserializer());
        consumer.subscribe(List.of("bank.users"));
        return consumer;
    }
}
