package com.bankflow.auth.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bankflow.auth.domain.user.Role;
import com.bankflow.auth.domain.user.event.UserRegistered;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

class UserRegisteredPublisherTests {

    private static final UserRegistered EVENT =
            new UserRegistered(
                    UUID.randomUUID(), "a@bankflow.test", false, Role.ROLE_CLIENT, Instant.now());

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);

    private final UserRegisteredPublisher publisher =
            new UserRegisteredPublisher(kafkaTemplate, JsonMapper.builder().build());

    @Test
    void unKafkaIndisponibleNeFaitPasEchouerLInscription() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenThrow(new TimeoutException("Kafka indisponible"));

        assertThatCode(() -> publisher.publish(EVENT)).doesNotThrowAnyException();
    }

    @Test
    void unEchecAsynchroneDePublicationNeRemontePas() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(
                        CompletableFuture.failedFuture(new TimeoutException("Kafka indisponible")));

        assertThatCode(() -> publisher.publish(EVENT)).doesNotThrowAnyException();
    }
}
