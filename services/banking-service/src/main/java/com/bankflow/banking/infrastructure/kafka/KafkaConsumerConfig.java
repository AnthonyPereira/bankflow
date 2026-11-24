package com.bankflow.banking.infrastructure.kafka;

import java.time.DateTimeException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.util.backoff.ExponentialBackOff;
import tools.jackson.core.JacksonException;

@Configuration(proxyBeanMethods = false)
class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    static final long INITIAL_INTERVAL_MS = 1_000;
    static final double MULTIPLIER = 2.0;
    static final long MAX_INTERVAL_MS = 30_000;
    static final int MAX_ATTEMPTS = 10;

    /**
     * Une erreur de traitement (base indisponible, conflit) est retentée avec une attente qui
     * double (1 s, 2 s, 4 s… jusqu'à 30 s), 10 fois au plus, puis le message est abandonné pour ne
     * pas bloquer la partition, en attendant la DLQ de BF-013. Une erreur de format
     * (désérialisation, JSON ou valeur invalide) ne se corrige pas en réessayant : elle est
     * abandonnée aussitôt. Aucun journal n'indique la valeur du message : elle peut contenir des
     * données personnelles. Les deux durées se règlent par propriété (les tests les
     * raccourcissent).
     */
    @Bean
    CommonErrorHandler createErrorHandler(
            @Value("${bankflow.kafka.retry.initial-interval-ms:" + INITIAL_INTERVAL_MS + "}")
                    long initialIntervalMs,
            @Value("${bankflow.kafka.retry.max-interval-ms:" + MAX_INTERVAL_MS + "}")
                    long maxIntervalMs) {
        var handler =
                new DefaultErrorHandler(
                        KafkaConsumerConfig::logAbandonedRecord,
                        createBackOff(initialIntervalMs, maxIntervalMs));
        handler.addNotRetryableExceptions(
                IllegalArgumentException.class, JacksonException.class, DateTimeException.class);
        handler.setRetryListeners(new LoggingRetryListener());
        // Le journal du framework peut citer la cause, donc la valeur : il passe en DEBUG au profit
        // des
        // nôtres
        handler.setLogLevel(KafkaException.Level.DEBUG);
        return handler;
    }

    static ExponentialBackOff createBackOff(long initialIntervalMs, long maxIntervalMs) {
        var backOff = new ExponentialBackOff(initialIntervalMs, MULTIPLIER);
        backOff.setMaxInterval(maxIntervalMs);
        backOff.setMaxAttempts(MAX_ATTEMPTS);
        return backOff;
    }

    private static void logAbandonedRecord(ConsumerRecord<?, ?> record, Exception exception) {
        log.error(
                "Message {}-{}@{} abandonné après échec ({})",
                record.topic(),
                record.partition(),
                record.offset(),
                describeCause(exception));
    }

    private static String describeCause(Exception exception) {
        var cause = exception.getCause() == null ? exception : exception.getCause();
        return cause.getClass().getName();
    }

    /** Journalise chaque nouvel essai sans jamais citer le message ni le texte de l'erreur. */
    private static final class LoggingRetryListener implements RetryListener {

        @Override
        public void failedDelivery(
                ConsumerRecord<?, ?> record, Exception exception, int deliveryAttempt) {
            log.warn(
                    "Message {}-{}@{} en échec (essai {}) : {}",
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    deliveryAttempt,
                    describeCause(exception));
        }
    }
}
