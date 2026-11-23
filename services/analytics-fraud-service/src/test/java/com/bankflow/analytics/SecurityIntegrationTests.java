package com.bankflow.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Le service protège ses API même si une requête contourne la Gateway. */
@Import({TestcontainersConfiguration.class, SecurityIntegrationTests.RoleProbeController.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SecurityIntegrationTests {

    private static final String ISSUER = "https://bankflow.local/auth";
    private static final String AUDIENCE = "bankflow-api";
    private static final RSAKey KEY = generateKey();
    private static final HttpServer JWKS = startJwksServer();

    private final HttpClient client = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    private int port;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "JWK_SET_URI",
                () -> "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/jwks.json");
    }

    /** Route de test : vérifie que le claim {@code role} devient bien une autorité. */
    @RestController
    static class RoleProbeController {

        @GetMapping("/test/admin")
        @PreAuthorize("hasRole('ADMIN')")
        String readAdminProbe() {
            return "ok";
        }
    }

    @AfterAll
    static void stopJwksServer() {
        JWKS.stop(0);
    }

    @Test
    void laSondeDeSanteEstPublique() throws Exception {
        assertThat(get("/actuator/health/liveness", null).statusCode()).isEqualTo(200);
    }

    @Test
    void uneRouteSansJwtEstRefuseeEnProblemDetail() throws Exception {
        var response = get("/ressource", null);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow())
                .contains("application/problem+json");
    }

    @Test
    void unJwtValideEstAccepte() throws Exception {
        assertThat(get("/ressource", createToken(ISSUER, AUDIENCE)).statusCode()).isEqualTo(404);
    }

    @Test
    void unJwtDUnAutreEmetteurEstRefuse() throws Exception {
        assertThat(get("/ressource", createToken("https://pirate.example", AUDIENCE)).statusCode())
                .isEqualTo(401);
    }

    @Test
    void unJwtDestineAUneAutreApiEstRefuse() throws Exception {
        assertThat(get("/ressource", createToken(ISSUER, "autre-api")).statusCode()).isEqualTo(401);
    }

    @Test
    void leClaimRoleClientNeDonnePasAccesAUneRouteAdmin() throws Exception {
        var response = get("/test/admin", createToken(ISSUER, AUDIENCE, "ROLE_CLIENT"));

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow())
                .contains("application/problem+json");
    }

    @Test
    void leClaimRoleAdminDonneAccesAUneRouteAdmin() throws Exception {
        var response = get("/test/admin", createToken(ISSUER, AUDIENCE, "ROLE_ADMIN"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("ok");
    }

    @Test
    void unRoleSansPrefixeOuAbsentNeDonnePasAccesAUneRouteAdmin() throws Exception {
        // Le claim est repris tel quel : « ADMIN » ne devient pas ROLE_ADMIN
        assertThat(get("/test/admin", createToken(ISSUER, AUDIENCE, "ADMIN")).statusCode())
                .isEqualTo(403);
        assertThat(get("/test/admin", createToken(ISSUER, AUDIENCE, null)).statusCode())
                .isEqualTo(403);
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String createToken(String issuer, String audience) throws Exception {
        return createToken(issuer, audience, "ROLE_CLIENT");
    }

    private static String createToken(String issuer, String audience, String role)
            throws Exception {
        var claims =
                new JWTClaimsSet.Builder()
                        .subject("6f1c2d84-7a51-4e0b-9a36-0a7d5c1e9f22")
                        .issuer(issuer)
                        .audience(audience)
                        .claim("role", role)
                        .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                        .build();
        var jwt =
                new SignedJWT(
                        new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.getKeyID()).build(),
                        claims);
        jwt.sign(new RSASSASigner(KEY));
        return jwt.serialize();
    }

    private static RSAKey generateKey() {
        try {
            return new RSAKeyGenerator(2048).keyID("cle-de-test").generate();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static HttpServer startJwksServer() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext(
                    "/jwks.json",
                    exchange -> {
                        var body =
                                new JWKSet(KEY.toPublicJWK())
                                        .toString()
                                        .getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().add("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, body.length);
                        exchange.getResponseBody().write(body);
                        exchange.close();
                    });
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
