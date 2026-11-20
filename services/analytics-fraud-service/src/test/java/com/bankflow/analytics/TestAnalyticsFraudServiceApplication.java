package com.bankflow.analytics;

import org.springframework.boot.SpringApplication;

public class TestAnalyticsFraudServiceApplication {

    public static void main(String[] args) {
        SpringApplication.from(AnalyticsFraudServiceApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
