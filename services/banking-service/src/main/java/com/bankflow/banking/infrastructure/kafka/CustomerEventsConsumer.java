package com.bankflow.banking.infrastructure.kafka;

import com.bankflow.banking.application.customer.CustomerRefSynchronizer;
import com.bankflow.banking.domain.customer.CustomerStatus;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Tient {@code customer_refs} à jour à partir de {@code CustomerCreated} et {@code
 * CustomerUpdated}. Un message invalide est journalisé sans son contenu (données personnelles
 * possibles) puis ignoré : la partition n'est jamais bloquée.
 */
@Component
class CustomerEventsConsumer {

    static final String TOPIC = "bank.customers";

    private static final Logger log = LoggerFactory.getLogger(CustomerEventsConsumer.class);
    private static final Set<String> HANDLED_TYPES = Set.of("CustomerCreated", "CustomerUpdated");
    private static final int MAX_EMAIL_LENGTH = 254;

    private final CustomerRefSynchronizer synchronizer;
    private final ObjectMapper objectMapper;

    CustomerEventsConsumer(CustomerRefSynchronizer synchronizer, ObjectMapper objectMapper) {
        this.synchronizer = synchronizer;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = TOPIC, groupId = "banking-service")
    void consumeCustomerEvent(String message) {
        parseEvent(message)
                .ifPresent(event ->
                        synchronizer.syncCustomer(
                                event.customerId(),
                                event.userId(),
                                event.email(),
                                event.status(),
                                event.occurredAt()));
    }

    private Optional<CustomerEvent> parseEvent(String message) {
        if (message == null) {
            log.warn("Message {} illisible ignoré", TOPIC);
            return Optional.empty();
        }
        try {
            var envelope = objectMapper.readTree(message);
            var type = readText(envelope, "eventType");
            if (type == null || !HANDLED_TYPES.contains(type)) {
                log.warn("Événement {} de type inconnu ignoré", TOPIC);
                return Optional.empty();
            }
            if (!envelope.path("eventVersion").isInt()
                    || envelope.get("eventVersion").asInt() != 1) {
                log.warn("Événement {} {} de version non gérée ignoré", TOPIC, type);
                return Optional.empty();
            }
            return buildEvent(envelope.path("payload"), readOccurredAt(envelope), type);
        } catch (JacksonException | IllegalArgumentException | DateTimeException exception) {
            log.warn("Événement {} invalide ignoré", TOPIC);
            return Optional.empty();
        }
    }

    private Optional<CustomerEvent> buildEvent(JsonNode payload, Instant occurredAt, String type) {
        var customerId = readText(payload, "customerId");
        var userId = readText(payload, "userId");
        var email = readText(payload, "email");
        var status = readText(payload, "status");
        if (customerId == null
                || userId == null
                || email == null
                || email.isBlank()
                || email.length() > MAX_EMAIL_LENGTH
                || status == null) {
            log.warn("Événement {} {} incomplet ignoré", TOPIC, type);
            return Optional.empty();
        }
        return Optional.of(
                new CustomerEvent(
                        UUID.fromString(customerId),
                        UUID.fromString(userId),
                        email,
                        CustomerStatus.valueOf(status),
                        occurredAt));
    }

    /** Date de l'état publié, tronquée à la microseconde comme PostgreSQL. */
    private static Instant readOccurredAt(JsonNode envelope) {
        var text = readText(envelope, "occurredAt");
        if (text == null) {
            throw new IllegalArgumentException("occurredAt absent");
        }
        return Instant.parse(text).truncatedTo(ChronoUnit.MICROS);
    }

    private static String readText(JsonNode node, String field) {
        var value = node.path(field);
        return value.isString() ? value.asString() : null;
    }

    private record CustomerEvent(
            UUID customerId,
            UUID userId,
            String email,
            CustomerStatus status,
            Instant occurredAt) {}
}
