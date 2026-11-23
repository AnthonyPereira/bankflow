package com.bankflow.banking.infrastructure.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration(proxyBeanMethods = false)
class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    private static final long RETRY_INTERVAL_MS = 1_000;
    private static final long MAX_RETRIES = 2;

    /**
     * Une erreur de traitement (base indisponible, conflit) est retentée deux fois, puis le message
     * est abandonné pour ne pas bloquer la partition, en attendant la DLQ de BF-013. Le journal
     * n'indique jamais la valeur du message : elle peut contenir des données personnelles.
     */
    @Bean
    CommonErrorHandler createErrorHandler() {
        return new DefaultErrorHandler(
                KafkaConsumerConfig::logAbandonedRecord,
                new FixedBackOff(RETRY_INTERVAL_MS, MAX_RETRIES));
    }

    private static void logAbandonedRecord(ConsumerRecord<?, ?> record, Exception exception) {
        var cause = exception.getCause() == null ? exception : exception.getCause();
        log.error(
                "Message {}-{}@{} abandonné après échec ({})",
                record.topic(),
                record.partition(),
                record.offset(),
                cause.getClass().getName());
    }
}
