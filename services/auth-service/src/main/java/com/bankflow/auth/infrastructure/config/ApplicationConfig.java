package com.bankflow.auth.infrastructure.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
class ApplicationConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Le préfixe {bcrypt} permettra de migrer plus tard vers Argon2 (ADR-001, section 2.2). */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
