package com.bankflow.auth.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.bankflow.auth.AuthApiTestSupport;
import com.bankflow.auth.infrastructure.security.jwt.JwtProperties;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/**
 * Le front renvoie son JWT expiré au moment même où il tente de rafraîchir sa session : les routes
 * publiques ignorent l'en-tête {@code Authorization}, les routes protégées le refusent toujours.
 */
class ExpiredBearerIntegrationTests extends AuthApiTestSupport {

    private static final String COOKIE = "bankflow_refresh";

    @Autowired private JwtEncoder jwtEncoder;
    @Autowired private JwtProperties properties;

    private String email;
    private String expiredBearer;

    @BeforeEach
    void setUp() throws Exception {
        email = generateUniqueEmail();
        var userId = parseJson(register(email, VALID_PASSWORD)).get("id").asString();
        expiredBearer = "Bearer " + createExpiredToken(userId);
    }

    @Test
    void laConnexionAvecUnJwtExpireReussit() throws Exception {
        var response =
                send(
                        buildRequest("/auth/login")
                                .header("Content-Type", "application/json")
                                .header("Authorization", expiredBearer)
                                .POST(
                                        HttpRequest.BodyPublishers.ofString(
                                                buildCredentials(email, VALID_PASSWORD))));

        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void leRefreshAvecUnJwtExpireReussit() throws Exception {
        var login = postJson("/auth/login", buildCredentials(email, VALID_PASSWORD));

        var response =
                send(
                        buildRequest("/auth/refresh")
                                .header("Cookie", COOKIE + "=" + extractCookieValue(login))
                                .header("Authorization", expiredBearer)
                                .POST(HttpRequest.BodyPublishers.noBody()));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(parseJson(response).get("accessToken").asString()).isNotBlank();
    }

    @Test
    void lesAutresRoutesPubliquesIgnorentUnJwtExpire() throws Exception {
        var logout =
                send(
                        buildRequest("/auth/logout")
                                .header("Authorization", expiredBearer)
                                .POST(HttpRequest.BodyPublishers.noBody()));
        var register =
                send(
                        buildRequest("/auth/register")
                                .header("Content-Type", "application/json")
                                .header("Authorization", expiredBearer)
                                .POST(
                                        HttpRequest.BodyPublishers.ofString(
                                                buildCredentials(
                                                        generateUniqueEmail(), VALID_PASSWORD))));
        var demoAccounts =
                send(buildRequest("/auth/demo-accounts").header("Authorization", expiredBearer));
        var jwks =
                send(buildRequest("/.well-known/jwks.json").header("Authorization", expiredBearer));

        assertThat(logout.statusCode()).isEqualTo(204);
        assertThat(register.statusCode()).isEqualTo(201);
        assertThat(demoAccounts.statusCode()).isEqualTo(200);
        assertThat(jwks.statusCode()).isEqualTo(200);
    }

    @Test
    void meRefuseToujoursUnJwtExpire() throws Exception {
        var response = send(buildRequest("/auth/me").header("Authorization", expiredBearer));

        assertThat(response.statusCode()).isEqualTo(401);
    }

    /** Signé par le service lui-même : seule l'expiration le rend invalide. */
    private String createExpiredToken(String userId) {
        var issuedAt = Instant.now().minus(Duration.ofHours(1));
        var claims =
                JwtClaimsSet.builder()
                        .id(UUID.randomUUID().toString())
                        .issuer(properties.issuer())
                        .audience(List.of(properties.audience()))
                        .subject(userId)
                        .claim("role", "ROLE_CLIENT")
                        .issuedAt(issuedAt)
                        .expiresAt(issuedAt.plus(properties.accessTokenTtl()))
                        .build();
        var header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static String extractCookieValue(HttpResponse<String> response) {
        var header = response.headers().firstValue("Set-Cookie").orElseThrow();
        return header.substring(COOKIE.length() + 1, header.indexOf(';'));
    }
}
