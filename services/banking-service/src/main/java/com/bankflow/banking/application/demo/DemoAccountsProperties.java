package com.bankflow.banking.application.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Réglages des données de démonstration.
 *
 * @param enabled faux : rien n'est créé (variable {@code DEMO_ACCOUNTS_ENABLED})
 * @param clientEmail email du client de démonstration qui reçoit les comptes
 */
@ConfigurationProperties("bankflow.demo")
public record DemoAccountsProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("demo.client@bankflow.local") String clientEmail) {}
