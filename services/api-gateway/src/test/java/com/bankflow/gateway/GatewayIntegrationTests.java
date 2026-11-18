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
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GatewayIntegrationTests {

    private static final String ISSUER = "https://bankflow.local/auth";
    private static final String AUDIENCE = "bankflow-api";
    private static final String USER_ID = "6f1c2d84-7a51-4e0b-9a36-0a7d5c1e9f22";

    private static final RSAKey SIGNING_KEY = generateKey("cle-de-test");
    private static final AtomicReference<ReceivedRequest> LAST_REQUEST = new AtomicReference<>();
    private static final HttpServer DOWNSTREAM = startDownstream();

    private final HttpClient client = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    private int port;

    record ReceivedRequest(String path, Map<String, List<String>> headers) {
        String header(String name) {
            return headers.entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                    .map(entry -> entry.getValue().getFirst())
                    .findFirst()
                    .orElse(null);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        var url = "http://127.0.0.1:" + DOWNSTREAM.getAddress().getPort();
        registry.add("JWK_SET_URI", () -> url + "/.well-known/jwks.json");
        registry.add("AUTH_SERVICE_URL", () -> url);
        registry.add("BANKING_SERVICE_URL", () -> url);
        var closedPort = unusedPort();
        registry.add("CUSTOMER_SERVICE_URL", () -> "http://127.0.0.1:" + closedPort);
    }

    @AfterAll
    static void stopDownstream() {
        DOWNSTREAM.stop(0);
    }

    @BeforeEach
    void resetDownstream() {
        LAST_REQUEST.set(null);
    }

    @Test
    void routePubliqueSansJwtRetireLePrefixeApi() throws Exception {
        var response = send(post("/api/auth/login"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(LAST_REQUEST.get().path()).isEqualTo("/auth/login");
    }

    @Test
    void routeProtegeeAvecJwtValideEstRelayee() throws Exception {
        var response = send(get("/api/accounts/42").header("Authorization", bearer(validToken())));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(LAST_REQUEST.get().path()).isEqualTo("/accounts/42");
    }

    @Test
    void routeProtegeeSansJwtEstRejeteeEnProblemDetail() throws Exception {
        var response = send(get("/api/accounts"));

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(type -> assertThat(type).contains("application/problem+json"));
        assertThat(response.body()).contains("\"status\":401", "correlationId");
        assertThat(LAST_REQUEST.get()).isNull();
    }

    @Test
    void jwtExpireEstRejete() throws Exception {
        var expired = token(SIGNING_KEY, ISSUER, AUDIENCE, Instant.now().minusSeconds(600));

        assertThat(send(get("/api/accounts").header("Authorization", bearer(expired))).statusCode())
                .isEqualTo(401);
    }

    @Test
    void jwtSigneParUneAutreCleEstRejete() throws Exception {
        var forged = token(generateKey("cle-de-test"), ISSUER, AUDIENCE, inOneHour());

        assertThat(send(get("/api/accounts").header("Authorization", bearer(forged))).statusCode())
                .isEqualTo(401);
    }

    @Test
    void jwtDUnAutreEmetteurOuPublicEstRejete() throws Exception {
        var otherIssuer = token(SIGNING_KEY, "https://pirate.example", AUDIENCE, inOneHour());
        var otherAudience = token(SIGNING_KEY, ISSUER, "autre-api", inOneHour());

        assertThat(
                        send(get("/api/accounts").header("Authorization", bearer(otherIssuer)))
                                .statusCode())
                .isEqualTo(401);
        assertThat(
                        send(get("/api/accounts").header("Authorization", bearer(otherAudience)))
                                .statusCode())
                .isEqualTo(401);
    }

    @Test
    void lesEnTetesDIdentiteUsurpesSontRemplacesParCeuxDuJwt() throws Exception {
        send(
                get("/api/accounts")
                        .header("Authorization", bearer(validToken()))
                        .header("X-User-Id", "usurpateur")
                        .header("X-User-Role", "ROLE_ADMIN"));

        assertThat(LAST_REQUEST.get().header("X-User-Id")).isEqualTo(USER_ID);
        assertThat(LAST_REQUEST.get().header("X-User-Role")).isEqualTo("ROLE_CLIENT");
    }

    @Test
    void lesEnTetesDIdentiteUsurpesSontRetiresDesRoutesPubliques() throws Exception {
        send(post("/api/auth/login").header("X-User-Id", "usurpateur").header("X-User-Role", "x"));

        assertThat(LAST_REQUEST.get().header("X-User-Id")).isNull();
        assertThat(LAST_REQUEST.get().header("X-User-Role")).isNull();
    }

    @Test
    void unCorrelationIdEstGenereQuandIlEstAbsent() throws Exception {
        var response = send(post("/api/auth/login"));

        var generated = response.headers().firstValue("X-Correlation-Id").orElseThrow();
        assertThat(generated).hasSize(36);
        assertThat(LAST_REQUEST.get().header("X-Correlation-Id")).isEqualTo(generated);
    }

    @Test
    void unCorrelationIdValideEstConserveEtUnInvalideEstRemplace() throws Exception {
        var kept = send(post("/api/auth/login").header("X-Correlation-Id", "trace-123"));
        var replaced = send(post("/api/auth/login").header("X-Correlation-Id", "faux id !"));

        assertThat(kept.headers().firstValue("X-Correlation-Id")).hasValue("trace-123");
        assertThat(replaced.headers().firstValue("X-Correlation-Id").orElseThrow())
                .isNotEqualTo("faux id !");
    }

    @Test
    void corsAutoriseUniquementLOrigineDuFrontend() throws Exception {
        var allowed = send(preflight("http://localhost:4200"));
        var refused = send(preflight("https://pirate.example"));

        assertThat(allowed.statusCode()).isEqualTo(200);
        assertThat(allowed.headers().firstValue("Access-Control-Allow-Origin"))
                .hasValue("http://localhost:4200");
        assertThat(allowed.headers().firstValue("Access-Control-Allow-Credentials"))
                .hasValue("true");
        assertThat(refused.statusCode()).isEqualTo(403);
        assertThat(refused.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
    }

    @Test
    void unServiceIndisponibleRepond503EnProblemDetail() throws Exception {
        var response = send(get("/api/customers/me").header("Authorization", bearer(validToken())));

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body()).contains("\"status\":503");
    }

    @Test
    void uneRouteInconnueRepond404EnProblemDetail() throws Exception {
        var response = send(get("/api/inconnu").header("Authorization", bearer(validToken())));

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("\"status\":404");
    }

    @Test
    void laSondeDeSanteResteAccessibleSansJwt() throws Exception {
        assertThat(send(get("/actuator/health/readiness")).statusCode()).isEqualTo(200);
    }

    private HttpRequest.Builder get(String path) {
        return HttpRequest.newBuilder(uri(path)).GET();
    }

    private HttpRequest.Builder post(String path) {
        return HttpRequest.newBuilder(uri(path)).POST(HttpRequest.BodyPublishers.ofString("{}"));
    }

    private HttpRequest.Builder preflight(String origin) {
        return HttpRequest.newBuilder(uri("/api/accounts"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "authorization");
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String validToken() throws Exception {
        return token(SIGNING_KEY, ISSUER, AUDIENCE, inOneHour());
    }

    private static Instant inOneHour() {
        return Instant.now().plusSeconds(3600);
    }

    private static String token(RSAKey key, String issuer, String audience, Instant expiresAt)
            throws Exception {
        var claims =
                new JWTClaimsSet.Builder()
                        .subject(USER_ID)
                        .issuer(issuer)
                        .audience(audience)
                        .claim("role", "ROLE_CLIENT")
                        .issueTime(Date.from(expiresAt.minusSeconds(900)))
                        .expirationTime(Date.from(expiresAt))
                        .build();
        var header =
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(key.getKeyID())
                        .type(JOSEObjectType.JWT)
                        .build();
        var jwt = new SignedJWT(header, claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    private static RSAKey generateKey(String keyId) {
        try {
            return new RSAKeyGenerator(2048).keyID(keyId).generate();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static int unusedPort() throws IOException {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static HttpServer startDownstream() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext(
                    "/",
                    exchange -> {
                        var path = exchange.getRequestURI().getPath();
                        exchange.getRequestBody().readAllBytes();
                        byte[] body;
                        if (path.equals("/.well-known/jwks.json")) {
                            body =
                                    new JWKSet(SIGNING_KEY.toPublicJWK())
                                            .toString()
                                            .getBytes(StandardCharsets.UTF_8);
                            exchange.getResponseHeaders().add("Content-Type", "application/json");
                        } else {
                            LAST_REQUEST.set(
                                    new ReceivedRequest(
                                            path, Map.copyOf(exchange.getRequestHeaders())));
                            body = "{}".getBytes(StandardCharsets.UTF_8);
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
