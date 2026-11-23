package com.bankflow.customer.infrastructure.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/** Erreurs du consommateur : deux nouvelles tentatives, puis le message est abandonné. */
@Configuration(proxyBeanMethods = false)
class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    private static final long RETRY_INTERVAL_MS = 1000L;
    private static final long MAX_RETRIES = 2L;

    /**
     * Sans file de rejet (DLQ, lot BF-013), le message abandonné est journalisé sans sa valeur :
     * elle contient des données personnelles. Seuls le topic, la partition, l'offset et le type
     * d'exception sont écrits.
     */
    @Bean("kafkaErrorHandler")
    DefaultErrorHandler createErrorHandler() {
        ConsumerRecordRecoverer recoverer =
                (record, exception) ->
                        log.error(
                                "Message abandonné après plusieurs essais : topic {}, partition {},"
                                        + " offset {}, exception {}",
                                record.topic(),
                                record.partition(),
                                record.offset(),
                                findRootCause(exception).getClass().getName());
        return new DefaultErrorHandler(recoverer, new FixedBackOff(RETRY_INTERVAL_MS, MAX_RETRIES));
    }

    private static Throwable findRootCause(Throwable exception) {
        var cause = exception;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }
}
