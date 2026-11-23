package com.bankflow.banking.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.DateTimeException;
import java.util.ArrayList;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.util.backoff.BackOffExecution;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(OutputCaptureExtension.class)
class KafkaConsumerConfigTests {

    private static final String SECRET = "secret.client@bankflow.test";

    private final Consumer<?, ?> consumer = mock(Consumer.class);
    private final MessageListenerContainer container = mock(MessageListenerContainer.class);
    private final DefaultErrorHandler handler =
            (DefaultErrorHandler) new KafkaConsumerConfig().createErrorHandler(1_000, 30_000);

    private static ConsumerRecord<String, String> createRecord() {
        return new ConsumerRecord<>("test.format", 0, 7L, "cle", "{\"email\":\"" + SECRET + "\"}");
    }

    private static JacksonException createJsonError() {
        try {
            JsonMapper.builder().build().readTree("{pas du json " + SECRET);
            throw new IllegalStateException("JSON valide");
        } catch (JacksonException exception) {
            return exception;
        }
    }

    private boolean handle(Exception cause) {
        var failure = new ListenerExecutionFailedException("échec sur " + SECRET, cause);
        return handler.handleOne(failure, createRecord(), consumer, container);
    }

    @Test
    void lAttenteDoubleDeUneSecondeJusquATrenteSecondesEtSArreteApresDixEssais() {
        BackOffExecution execution = KafkaConsumerConfig.createBackOff(1_000, 30_000).start();

        var intervals = new ArrayList<Long>();
        long next;
        while ((next = execution.nextBackOff()) != BackOffExecution.STOP) {
            intervals.add(next);
        }

        assertThat(intervals)
                .containsExactly(
                        1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L, 30_000L, 30_000L,
                        30_000L);
    }

    @Test
    void uneErreurDeTraitementEstRetentee(CapturedOutput output) {
        var recovered = handle(new IllegalStateException("base indisponible"));

        assertThat(recovered).isFalse();
        assertThat(output.getAll()).contains("test.format-0@7 en échec (essai 1)");
    }

    @Test
    void lesErreursDeFormatSontAbandonneesSansEtreRetentees(CapturedOutput output) {
        var formatErrors =
                new Exception[] {
                    new IllegalArgumentException(SECRET),
                    new DateTimeException(SECRET),
                    createJsonError()
                };

        for (var error : formatErrors) {
            assertThat(handle(error)).as(error.getClass().getSimpleName()).isTrue();
        }

        assertThat(output.getAll()).contains("Message test.format-0@7 abandonné après échec");
    }

    @Test
    void aucuneValeurDeMessageNApparaitDansLesJournaux(CapturedOutput output) {
        handle(new IllegalStateException(SECRET));
        handle(new IllegalArgumentException(SECRET));

        assertThat(output.getAll()).contains("test.format-0@7").doesNotContain(SECRET);
    }
}
