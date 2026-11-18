package com.bankflow.auth.infrastructure.kafka.demo;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.TopicBuilder;

/** Démonstration Kafka de BF-003, active uniquement avec le profil {@code kafka-demo}. */
@Configuration(proxyBeanMethods = false)
@Profile("kafka-demo")
class KafkaDemoConfig {

    static final String TOPIC = "bank.demo";

    @Bean
    NewTopic demoTopic() {
        return TopicBuilder.name(TOPIC).partitions(3).replicas(1).build();
    }
}
