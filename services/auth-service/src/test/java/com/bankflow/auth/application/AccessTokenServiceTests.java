package com.bankflow.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bankflow.auth.domain.Role;
import com.bankflow.auth.domain.User;
import com.bankflow.auth.infrastructure.security.JwtProperties;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class AccessTokenServiceTests {

    private static final String ISSUER = "https://bankflow.local/auth";
    private static final JwtProperties PROPERTIES =
            new JwtProperties(ISSUER, "bankflow-api", Duration.ofMinutes(15), "", "cle-test");

    private static RSAKey key;

    @BeforeAll
    static void generateKey() throws Exception {
        key = new RSAKeyGenerator(2048).keyID("cle-test").generate();
    }

    @Test
    void leJwtEmisEstValideEtPorteLIdentiteDeLUtilisateur() throws Exception {
        var user = User.register("a@bankflow.test", "{noop}x", Role.ROLE_ADMIN, Instant.now());

        var token = serviceAt(Instant.now()).issue(user);

        var jwt = decoder().decode(token.value());
        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getClaimAsString("role")).isEqualTo("ROLE_ADMIN");
        assertThat(token.expiresInSeconds()).isEqualTo(900);
    }

    @Test
    void unJwtExpireEstRejete() throws Exception {
        var user = User.register("b@bankflow.test", "{noop}x", Role.ROLE_CLIENT, Instant.now());

        var token = serviceAt(Instant.now().minus(Duration.ofHours(1))).issue(user);

        assertThatThrownBy(() -> decoder().decode(token.value()))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("expired");
    }

    private static AccessTokenService serviceAt(Instant now) {
        var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        return new AccessTokenService(encoder, PROPERTIES, Clock.fixed(now, ZoneOffset.UTC));
    }

    private static NimbusJwtDecoder decoder() throws Exception {
        var decoder = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
        return decoder;
    }
}
