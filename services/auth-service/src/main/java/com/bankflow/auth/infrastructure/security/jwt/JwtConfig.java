package com.bankflow.auth.infrastructure.security.jwt;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
class JwtConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

    /**
     * Une clé éphémère reçoit un {@code kid} unique : après un redémarrage, la Gateway et les
     * autres services voient un {@code kid} inconnu et rechargent le JWKS, au lieu de refuser les
     * nouveaux jetons avec l'ancienne clé gardée en cache. Une clé fournie garde le {@code kid}
     * configuré.
     */
    @Bean("jwtSigningKey")
    RSAKey createJwtSigningKey(JwtProperties properties, Environment environment) {
        if (properties.privateKey() != null && !properties.privateKey().isBlank()) {
            return RsaKeyLoader.loadFromBase64(properties.privateKey(), properties.keyId());
        }
        if (environment.acceptsProfiles(Profiles.of("prod-sim"))) {
            throw new IllegalStateException("JWT_PRIVATE_KEY est obligatoire avec prod-sim.");
        }
        log.warn("JWT_PRIVATE_KEY est vide : une clé éphémère est générée pour ce démarrage.");
        return RsaKeyLoader.generate(properties.keyId() + "-" + UUID.randomUUID());
    }

    @Bean("jwtEncoder")
    JwtEncoder createJwtEncoder(RSAKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwtSigningKey)));
    }

    /** Le service vérifie ses propres jetons avec sa clé publique, sans appel réseau. */
    @Bean("jwtDecoder")
    JwtDecoder createJwtDecoder(RSAKey jwtSigningKey, JwtProperties properties)
            throws JOSEException {
        var decoder = NimbusJwtDecoder.withPublicKey(jwtSigningKey.toRSAPublicKey()).build();
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefaultWithIssuer(properties.issuer()),
                        new JwtClaimValidator<List<String>>(
                                JwtClaimNames.AUD,
                                audience ->
                                        audience != null
                                                && audience.contains(properties.audience()))));
        return decoder;
    }
}
