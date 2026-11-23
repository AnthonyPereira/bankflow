package com.bankflow.customer.infrastructure.kafka;

import com.bankflow.customer.domain.customer.CustomerCreated;
import com.bankflow.customer.domain.customer.CustomerStatus;
import com.bankflow.customer.domain.customer.CustomerUpdated;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

/**
 * Publie {@code CustomerCreated} et {@code CustomerUpdated} après la validation de la transaction.
 * Une panne de Kafka ne fait pas échouer la requête : l'événement est alors perdu, ce que l'Outbox
 * (lot ultérieur) corrigera. Les coordonnées personnelles ne quittent jamais le service.
 */
@Component
class CustomerEventPublisher {

    static final String TOPIC = "bank.customers";

    private static final Logger log = LoggerFactory.getLogger(CustomerEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    CustomerEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void publishCreated(CustomerCreated event) {
        send(
                "CustomerCreated",
                event.customerId(),
                event.userId(),
                event.email(),
                event.status(),
                event.demo(),
                event.occurredAt());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void publishUpdated(CustomerUpdated event) {
        send(
                "CustomerUpdated",
                event.customerId(),
                event.userId(),
                event.email(),
                event.status(),
                event.demo(),
                event.occurredAt());
    }

    private void send(
            String eventType,
            UUID customerId,
            UUID userId,
            String email,
            CustomerStatus status,
            boolean demo,
            Instant occurredAt) {
        var key = customerId.toString();
        try {
            var payload = new LinkedHashMap<String, Object>();
            payload.put("customerId", customerId);
            payload.put("userId", userId);
            payload.put("email", email);
            payload.put("status", status);
            payload.put("demo", demo);
            kafkaTemplate
                    .send(
                            TOPIC,
                            key,
                            objectMapper.writeValueAsString(
                                    buildEnvelope(eventType, occurredAt, payload)))
                    .whenComplete((result, failure) -> {
                        if (failure != null) {
                            log.error("{} non publié pour {}", eventType, key, failure);
                        }
                    });
        } catch (RuntimeException exception) {
            log.error("{} non publié pour {}", eventType, key, exception);
        }
    }

    private static Map<String, Object> buildEnvelope(
            String eventType, Instant occurredAt, Map<String, Object> payload) {
        var envelope = new LinkedHashMap<String, Object>();
        envelope.put("eventId", UUID.randomUUID());
        envelope.put("eventType", eventType);
        envelope.put("eventVersion", 1);
        envelope.put("occurredAt", occurredAt);
        envelope.put("producer", "customer-service");
        envelope.put("payload", payload);
        return envelope;
    }
}
