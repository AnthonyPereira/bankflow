package com.bankflow.auth.infrastructure.kafka.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.bankflow.auth.TestcontainersConfiguration;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles({"test", "kafka-demo"})
class KafkaDemoIntegrationTests {

    @Autowired private KafkaDemoProducer producer;
    @Autowired private KafkaDemoConsumer consumer;

    @Test
    void unMessageProduitEstConsommeAvecSonEnveloppe() throws Exception {
        var message = "bonjour-" + UUID.randomUUID();

        producer.publish("compte-1", message).get(30, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() ->
                        assertThat(consumer.received())
                                .anySatisfy(event -> {
                                    assertThat(event.payload()).containsEntry("message", message);
                                    assertThat(event.eventId()).isNotNull();
                                    assertThat(event.eventType()).isEqualTo("DemoMessageSent");
                                    assertThat(event.eventVersion()).isEqualTo(1);
                                    assertThat(event.producer()).isEqualTo("auth-service");
                                    assertThat(event.occurredAt()).isNotNull();
                                }));
    }

    @Test
    void uneMemeCleArriveToujoursDansLaMemePartition() throws Exception {
        var first = producer.publish("compte-2", "a").get(30, TimeUnit.SECONDS);
        var second = producer.publish("compte-2", "b").get(30, TimeUnit.SECONDS);

        assertThat(second.getRecordMetadata().partition())
                .isEqualTo(first.getRecordMetadata().partition());
        assertThat(second.getRecordMetadata().offset())
                .isGreaterThan(first.getRecordMetadata().offset());
    }
}
