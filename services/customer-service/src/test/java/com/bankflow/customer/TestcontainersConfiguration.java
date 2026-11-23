package com.bankflow.customer;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean("kafkaContainer")
    @ServiceConnection
    KafkaContainer createKafkaContainer() {
        return new KafkaContainer(DockerImageName.parse("apache/kafka-native:4.4.0"));
    }

    @Bean("postgresContainer")
    @ServiceConnection
    PostgreSQLContainer createPostgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));
    }

    /**
     * Les topics existent avant le démarrage des consommateurs : sinon ils ne découvrent un topic
     * créé à la volée qu'au prochain rafraîchissement des métadonnées (plusieurs minutes).
     */
    @Bean("usersTopic")
    NewTopic createUsersTopic() {
        return TopicBuilder.name("bank.users").partitions(1).replicas(1).build();
    }

    @Bean("customersTopic")
    NewTopic createCustomersTopic() {
        return TopicBuilder.name("bank.customers").partitions(1).replicas(1).build();
    }
}
