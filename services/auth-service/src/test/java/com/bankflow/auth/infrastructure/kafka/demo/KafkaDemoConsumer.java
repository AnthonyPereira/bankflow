package com.bankflow.auth.infrastructure.kafka.demo;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Lit les événements de démonstration. L'offset est validé une fois le message traité. */
@Component
@Profile("kafka-demo")
public class KafkaDemoConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaDemoConsumer.class);

    private final ObjectMapper objectMapper;
    private final List<DemoEnvelope> received = new CopyOnWriteArrayList<>();

    KafkaDemoConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = KafkaDemoConfig.TOPIC, groupId = "auth-service-demo")
    void onMessage(String value) {
        var envelope = objectMapper.readValue(value, DemoEnvelope.class);
        received.add(envelope);
        log.info("Événement de démonstration reçu : {}", envelope.eventId());
    }

    public List<DemoEnvelope> received() {
        return List.copyOf(received);
    }
}
