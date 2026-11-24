package com.bankflow.customer.infrastructure.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ApplicationConfig {

    @Bean("clock")
    Clock createClock() {
        return Clock.systemUTC();
    }
}
