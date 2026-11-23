package com.bankflow.customer.infrastructure.kafka;

import com.bankflow.customer.application.customer.CustomerRegistrationService;
import com.bankflow.customer.domain.customer.Customer;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Crée les clients à partir de {@code UserRegistered}. Un message inexploitable est journalisé
 * (sans son contenu) puis ignoré : il ne doit jamais bloquer la partition (DLQ en BF-013).
 */
@Component
class UserRegisteredConsumer {

    static final String TOPIC = "bank.users";
    static final String GROUP_ID = "customer-service";

    private static final int MAX_EMAIL_LENGTH = 254;
    private static final Logger log = LoggerFactory.getLogger(UserRegisteredConsumer.class);

    private final CustomerRegistrationService registrationService;
    private final ObjectMapper objectMapper;

    UserRegisteredConsumer(
            CustomerRegistrationService registrationService, ObjectMapper objectMapper) {
        this.registrationService = registrationService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = TOPIC, groupId = GROUP_ID)
    void consume(ConsumerRecord<String, String> record) {
        var event = parse(record);
        if (event == null) {
            return;
        }
        try {
            registrationService.registerUser(event.userId(), event.email(), event.demo());
        } catch (DataIntegrityViolationException concurrentInsert) {
            // Un message concurrent a créé le client : la contrainte d'unicité a tranché, on rejoue
            // pour traiter ce message comme un client existant.
            registrationService.registerUser(event.userId(), event.email(), event.demo());
        }
    }

    private ParsedEvent parse(ConsumerRecord<String, String> record) {
        if (record.value() == null) {
            return skip(record, "message vide");
        }
        JsonNode envelope;
        try {
            envelope = objectMapper.readTree(record.value());
        } catch (JacksonException exception) {
            return skip(record, "JSON illisible");
        }
        if (!"UserRegistered".equals(envelope.path("eventType").asString(""))) {
            return skip(record, "type d'événement inconnu");
        }
        if (envelope.path("eventVersion").asInt(-1) != 1) {
            return skip(record, "version d'événement non gérée");
        }
        var payload = envelope.path("payload");
        var userId = parseUuid(payload.path("userId").asString(""));
        var email = payload.path("email").asString("").strip();
        var demo = payload.path("demo");
        if (userId == null) {
            return skip(record, "userId invalide");
        }
        if (email.isEmpty() || email.length() > MAX_EMAIL_LENGTH) {
            return skip(record, "email invalide");
        }
        if (!demo.isMissingNode() && !demo.isNull() && !demo.isBoolean()) {
            return skip(record, "demo invalide");
        }
        return new ParsedEvent(userId, Customer.normalizeEmail(email), demo.asBoolean(false));
    }

    private static UUID parseUuid(String text) {
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static ParsedEvent skip(ConsumerRecord<String, String> record, String reason) {
        log.warn(
                "UserRegistered ignoré ({}) : partition {}, offset {}",
                reason,
                record.partition(),
                record.offset());
        return null;
    }

    private record ParsedEvent(UUID userId, String email, boolean demo) {}
}
