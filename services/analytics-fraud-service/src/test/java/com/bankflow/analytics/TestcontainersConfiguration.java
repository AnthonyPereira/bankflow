package com.bankflow.analytics;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
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
}
