package com.bankflow.auth.application.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bankflow.auth.domain.user.Role;
import com.bankflow.auth.domain.user.User;
import com.bankflow.auth.infrastructure.security.jwt.JwtProperties;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class AccessTokenServiceTests {

    private static final String ISSUER = "https://bankflow.local/auth";
    private static final String AUDIENCE = "bankflow-api";
    private static final JwtProperties PROPERTIES =
            new JwtProperties(ISSUER, AUDIENCE, Duration.ofMinutes(15), "", "cle-configuree");

    private static RSAKey key;

    @BeforeAll
    static void generateKey() throws Exception {
        // Un kid différent de celui configuré : le jeton doit porter celui de la clé qui signe
        key = new RSAKeyGenerator(2048).keyID("cle-de-signature").generate();
    }

    @Test
    void leJwtEmisEstValideEtPorteLIdentiteDeLUtilisateur() throws Exception {
        var user = User.register("a@bankflow.test", "{noop}x", Role.ROLE_ADMIN, Instant.now());

        var token = createServiceAt(Instant.now(), PROPERTIES).issue(user);

        var jwt = createDecoder().decode(token.value());
        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getClaimAsString("role")).isEqualTo("ROLE_ADMIN");
        assertThat(jwt.getClaimAsBoolean("demo")).isFalse();
        assertThat(jwt.getAudience()).containsExactly(AUDIENCE);
        assertThat(jwt.getId()).isNotBlank();
        assertThat(jwt.getHeaders()).containsEntry("kid", "cle-de-signature");
        assertThat(token.expiresInSeconds()).isEqualTo(900);
    }

    @Test
    void leJwtDUnCompteDeDemonstrationPorteLeClaimDemoVrai() throws Exception {
        var user = User.registerDemo("d@bankflow.test", "{noop}x", Role.ROLE_ADMIN, Instant.now());

        var token = createServiceAt(Instant.now(), PROPERTIES).issue(user);

        assertThat(createDecoder().decode(token.value()).getClaimAsBoolean("demo")).isTrue();
    }

    @Test
    void chaqueJwtPorteUnIdentifiantUnique() throws Exception {
        var user = User.register("c@bankflow.test", "{noop}x", Role.ROLE_CLIENT, Instant.now());
        var service = createServiceAt(Instant.now(), PROPERTIES);

        var first = createDecoder().decode(service.issue(user).value());
        var second = createDecoder().decode(service.issue(user).value());

        assertThat(first.getId()).isNotEqualTo(second.getId());
    }

    @Test
    void unJwtExpireEstRejete() throws Exception {
        var user = User.register("b@bankflow.test", "{noop}x", Role.ROLE_CLIENT, Instant.now());

        var token =
                createServiceAt(Instant.now().minus(Duration.ofHours(1)), PROPERTIES).issue(user);

        assertThatThrownBy(() -> createDecoder().decode(token.value()))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void unJwtPourUneAutreAudienceEstRejete() throws Exception {
        var user = User.register("d@bankflow.test", "{noop}x", Role.ROLE_CLIENT, Instant.now());
        var otherAudience =
                new JwtProperties(ISSUER, "autre-api", Duration.ofMinutes(15), "", "cle");

        var token = createServiceAt(Instant.now(), otherAudience).issue(user);

        assertThatThrownBy(() -> createDecoder().decode(token.value()))
                .isInstanceOf(JwtValidationException.class);
    }

    private static AccessTokenService createServiceAt(Instant now, JwtProperties properties) {
        var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        return new AccessTokenService(encoder, properties, Clock.fixed(now, ZoneOffset.UTC));
    }

    /** Même validation que le service : émetteur, dates et audience. */
    private static NimbusJwtDecoder createDecoder() throws Exception {
        var decoder = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build();
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefaultWithIssuer(ISSUER),
                        new JwtClaimValidator<List<String>>(
                                JwtClaimNames.AUD,
                                audience -> audience != null && audience.contains(AUDIENCE))));
        return decoder;
    }
}
