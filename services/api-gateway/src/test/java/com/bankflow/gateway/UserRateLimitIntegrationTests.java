package com.bankflow.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JOSEObjectType;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "bankflow.rate-limit.transfers-per-minute=100",
            "bankflow.rate-limit.operations-per-minute=100",
            "bankflow.rate-limit.transfers-per-user-per-minute=2"
        })
@ActiveProfiles("test")
class UserRateLimitIntegrationTests {

    private static final String ISSUER = "https://bankflow.local/auth";
    private static final String AUDIENCE = "bankflow-api";

    private static final RSAKey SIGNING_KEY = generateKey();
    private static final HttpServer DOWNSTREAM = startDownstream();

    private final HttpClient client = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    private int port;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        var url = "http://127.0.0.1:" + DOWNSTREAM.getAddress().getPort();
        registry.add("JWK_SET_URI", () -> url + "/.well-known/jwks.json");
        registry.add("BANKING_SERVICE_URL", () -> url);
    }

    @AfterAll
    static void stopDownstream() {
        DOWNSTREAM.stop(0);
    }

    @Test
    void laLimiteSuitLUtilisateurMemeEnChangeantDAdresseIp() throws Exception {
        var token = createToken("user-a");
        assertThat(send("POST", "/api/transfers", token, "203.0.113.1").statusCode())
                .isEqualTo(200);
        assertThat(send("POST", "/api/transfers", token, "203.0.113.2").statusCode())
                .isEqualTo(200);

        var refused = send("POST", "/api/transfers", token, "203.0.113.3");

        assertThat(refused.statusCode()).isEqualTo(429);
        assertThat(refused.headers().firstValue("Retry-After")).isPresent();
        assertThat(refused.body()).contains("\"status\":429");
    }

    @Test
    void unAutreUtilisateurNEstPasAffecte() throws Exception {
        var token = createToken("user-b");
        for (int attempt = 0; attempt < 3; attempt++) {
            send("POST", "/api/transfers", token, "203.0.113.10");
        }

        assertThat(
                        send("POST", "/api/transfers", createToken("user-c"), "203.0.113.10")
                                .statusCode())
                .isEqualTo(200);
    }

    @Test
    void unCheminEncodeEstCompteDansLaMemeLimite() throws Exception {
        var token = createToken("user-d");
        send("POST", "/api/transfers", token, "203.0.113.20");
        send("POST", "/api/%74ransfers", token, "203.0.113.21");

        assertThat(send("POST", "/api/transfers", token, "203.0.113.22").statusCode())
                .isEqualTo(429);
    }

    @Test
    void lesAutresRequetesNeSontPasCompteesParCeFiltre() throws Exception {
        var token = createToken("user-e");
        for (int attempt = 0; attempt < 4; attempt++) {
            assertThat(send("GET", "/api/transfers", token, "203.0.113.30").statusCode())
                    .isEqualTo(200);
            assertThat(
                            send("POST", "/api/accounts/a1/deposits", token, "203.0.113.30")
                                    .statusCode())
                    .isEqualTo(200);
        }

        assertThat(send("POST", "/api/transfers", token, "203.0.113.30").statusCode())
                .isEqualTo(200);
    }

    private HttpResponse<String> send(String method, String path, String token, String clientIp)
            throws Exception {
        var request =
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .header("Authorization", "Bearer " + token)
                        .header("X-Forwarded-For", clientIp)
                        .method(
                                method,
                                "POST".equals(method)
                                        ? HttpRequest.BodyPublishers.ofString("{}")
                                        : HttpRequest.BodyPublishers.noBody())
                        .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String createToken(String subject) throws Exception {
        var expiresAt = Instant.now().plusSeconds(3600);
        var claims =
                new JWTClaimsSet.Builder()
                        .subject(subject)
                        .issuer(ISSUER)
                        .audience(AUDIENCE)
                        .claim("role", "ROLE_CLIENT")
                        .issueTime(Date.from(expiresAt.minusSeconds(900)))
                        .expirationTime(Date.from(expiresAt))
                        .build();
        var header =
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(SIGNING_KEY.getKeyID())
                        .type(JOSEObjectType.JWT)
                        .build();
        var jwt = new SignedJWT(header, claims);
        jwt.sign(new RSASSASigner(SIGNING_KEY));
        return jwt.serialize();
    }

    private static RSAKey generateKey() {
        try {
            return new RSAKeyGenerator(2048).keyID("cle-de-test").generate();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static HttpServer startDownstream() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext(
                    "/",
                    exchange -> {
                        exchange.getRequestBody().readAllBytes();
                        var body = "{}".getBytes(StandardCharsets.UTF_8);
                        if (exchange.getRequestURI().getPath().equals("/.well-known/jwks.json")) {
                            body =
                                    new JWKSet(SIGNING_KEY.toPublicJWK())
                                            .toString()
                                            .getBytes(StandardCharsets.UTF_8);
                            exchange.getResponseHeaders().add("Content-Type", "application/json");
                        }
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
