package com.bankflow.banking.infrastructure.config;

import com.bankflow.banking.domain.account.AccountNumberGenerator;
import jakarta.servlet.DispatcherType;
import java.security.SecureRandom;
import java.time.Clock;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.ForwardedHeaderFilter;

@Configuration(proxyBeanMethods = false)
class ApplicationConfig {

    @Bean("systemClock")
    Clock createClock() {
        return Clock.systemUTC();
    }

    /**
     * Spring 7.1 n'applique plus {@code X-Forwarded-Prefix} par défaut : on l'active pour que les
     * URL publiques (en-tête {@code Location}) contiennent le préfixe {@code /api} de la Gateway.
     */
    @Bean
    FilterRegistrationBean<ForwardedHeaderFilter> createForwardedHeaderFilter() {
        var filter = new ForwardedHeaderFilter();
        filter.setUseForwardedPrefix(true);
        var registration = new FilterRegistrationBean<>(filter);
        registration.setDispatcherTypes(
                DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean("accountNumberGenerator")
    AccountNumberGenerator createAccountNumberGenerator() {
        return new AccountNumberGenerator(new SecureRandom());
    }
}
