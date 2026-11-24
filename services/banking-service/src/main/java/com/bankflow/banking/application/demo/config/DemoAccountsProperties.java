package com.bankflow.banking.application.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Réglages des données de démonstration.
 *
 * @param enabled faux : rien n'est créé (variable {@code DEMO_ACCOUNTS_ENABLED})
 */
@ConfigurationProperties("bankflow.demo")
public record DemoAccountsProperties(@DefaultValue("true") boolean enabled) {}
