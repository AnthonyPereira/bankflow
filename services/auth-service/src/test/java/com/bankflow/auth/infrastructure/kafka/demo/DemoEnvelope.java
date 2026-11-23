package com.bankflow.auth.infrastructure.kafka.demo;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Enveloppe commune décrite dans {@code contracts/events/envelope.md}. */
public record DemoEnvelope(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String producer,
        Map<String, Object> payload) {}
