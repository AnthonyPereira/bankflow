package com.bankflow.auth.infrastructure.kafka;

import com.bankflow.auth.domain.user.event.UserRegistered;
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
 * Publie {@code UserRegistered} après la validation de l'inscription. Une panne de Kafka ne fait
 * pas échouer l'inscription : l'événement est alors perdu, ce que l'Outbox de BF-011 corrigera.
 */
@Component
class UserRegisteredPublisher {

    static final String TOPIC = "bank.users";

    private static final Logger log = LoggerFactory.getLogger(UserRegisteredPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    UserRegisteredPublisher(
            KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void publish(UserRegistered event) {
        send(event);
    }

    /**
     * Envoie l'événement sur Kafka, sans jamais lever : la panne est journalisée.
     *
     * @return {@code false} si l'envoi a déjà échoué (Kafka injoignable), {@code true} sinon
     */
    boolean send(UserRegistered event) {
        var key = event.userId().toString();
        try {
            var future =
                    kafkaTemplate
                            .send(TOPIC, key, objectMapper.writeValueAsString(buildEnvelope(event)))
                            .whenComplete((result, failure) -> {
                                if (failure != null) {
                                    log.error("UserRegistered non publié pour {}", key, failure);
                                }
                            });
            return !future.isCompletedExceptionally();
        } catch (RuntimeException exception) {
            log.error("UserRegistered non publié pour {}", key, exception);
            return false;
        }
    }

    private static Map<String, Object> buildEnvelope(UserRegistered event) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("userId", event.userId());
        payload.put("email", event.email());
        payload.put("demo", event.demo());
        payload.put("role", event.role().name());

        var envelope = new LinkedHashMap<String, Object>();
        envelope.put("eventId", UUID.randomUUID());
        envelope.put("eventType", "UserRegistered");
        envelope.put("eventVersion", 1);
        envelope.put("occurredAt", event.occurredAt());
        envelope.put("producer", "auth-service");
        envelope.put("payload", payload);
        return envelope;
    }
}
