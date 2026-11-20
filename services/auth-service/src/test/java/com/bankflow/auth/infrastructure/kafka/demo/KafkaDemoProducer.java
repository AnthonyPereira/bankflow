package com.bankflow.auth.infrastructure.kafka.demo;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Publie un événement en JSON. La clé choisit la partition, donc l'ordre de lecture. */
@Component
@Profile("kafka-demo")
public class KafkaDemoProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    KafkaDemoProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public CompletableFuture<SendResult<String, String>> publish(String key, String message) {
        var envelope =
                new DemoEnvelope(
                        UUID.randomUUID(),
                        "DemoMessageSent",
                        1,
                        Instant.now(),
                        "auth-service",
                        Map.of("message", message));
        return kafkaTemplate.send(
                KafkaDemoConfig.TOPIC, key, objectMapper.writeValueAsString(envelope));
    }
}
