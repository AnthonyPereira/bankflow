package com.bankflow.banking.infrastructure.config;

import com.bankflow.banking.domain.account.AccountNumberGenerator;
import java.security.SecureRandom;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ApplicationConfig {

    @Bean("systemClock")
    Clock createClock() {
        return Clock.systemUTC();
    }

    @Bean("accountNumberGenerator")
    AccountNumberGenerator createAccountNumberGenerator() {
        return new AccountNumberGenerator(new SecureRandom());
    }
}
