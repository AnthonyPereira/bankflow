package com.bankflow.banking;

import org.springframework.boot.SpringApplication;

public class TestBankingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.from(BankingServiceApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
