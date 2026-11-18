package com.bankflow.auth.infrastructure.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("bankflow.jwt")
public record JwtProperties(
        String issuer, String audience, Duration accessTokenTtl, String privateKey, String keyId) {}
