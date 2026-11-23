package com.bankflow.auth.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.bankflow.auth.AuthApiTestSupport;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class UserRegisteredReplayerIntegrationTests extends AuthApiTestSupport {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired private KafkaContainer kafka;
    @Autowired private UserRegisteredReplayer replayer;

    @Test
    void auDemarrageLesComptesDeDemonstrationSontRepublieAvecLIndicateurDemo() {
        var payloads =
                awaitPayloads(all ->
                        hasPayloadFor(all, "demo.client@bankflow.local")
                                && hasPayloadFor(all, "demo.admin@bankflow.local"));

        for (var email : List.of("demo.client@bankflow.local", "demo.admin@bankflow.local")) {
            var payload =
                    payloads.stream().filter(p -> isEmail(p, email)).findFirst().orElseThrow();
            assertThat(payload.get("demo").asBoolean()).isTrue();
        }
    }

    @Test
    void leRejeuRepublieUnUtilisateurExistantAvecDemoFaux() throws Exception {
        var email = generateUniqueEmail();
        var userId = parseJson(register(email, VALID_PASSWORD)).get("id").asString();
        awaitPayloads(all -> countFor(all, userId) >= 1);

        var replayed = replayer.replayAll();

        assertThat(replayed).isPositive();
        var payloads = awaitPayloads(all -> countFor(all, userId) >= 2);
        assertThat(payloads.stream().filter(p -> userId.equals(p.get("userId").asString())))
                .hasSizeGreaterThanOrEqualTo(2)
                .allSatisfy(payload -> {
                    assertThat(payload.get("email").asString()).isEqualTo(email);
                    assertThat(payload.get("demo").asBoolean()).isFalse();
                });
    }

    private static boolean hasPayloadFor(List<JsonNode> payloads, String email) {
        return payloads.stream().anyMatch(payload -> isEmail(payload, email));
    }

    private static boolean isEmail(JsonNode payload, String email) {
        return email.equals(payload.get("email").asString());
    }

    private static long countFor(List<JsonNode> payloads, String userId) {
        return payloads.stream().filter(p -> userId.equals(p.get("userId").asString())).count();
    }

    /** Lit le topic depuis le début jusqu'à ce que la condition soit remplie (30 s au plus). */
    private List<JsonNode> awaitPayloads(Predicate<List<JsonNode>> condition) {
        var payloads = new ArrayList<JsonNode>();
        try (var consumer = createConsumer()) {
            var deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            while (System.nanoTime() < deadline) {
                for (var record : consumer.poll(Duration.ofMillis(500))) {
                    payloads.add(JSON.readTree(record.value()).get("payload"));
                }
                if (condition.test(payloads)) {
                    return payloads;
                }
            }
        }
        throw new AssertionError("UserRegistered attendu non reçu");
    }

    private KafkaConsumer<String, String> createConsumer() {
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
