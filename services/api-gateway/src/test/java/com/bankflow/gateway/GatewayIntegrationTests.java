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
        String findHeader(String name) {
            return headers.entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                    .map(entry -> entry.getValue().getFirst())
                    .findFirst()
                    .orElse(null);
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) throws IOException {
        var url = "http://127.0.0.1:" + DOWNSTREAM.getAddress().getPort();
        registry.add("JWK_SET_URI", () -> url + "/.well-known/jwks.json");
        registry.add("AUTH_SERVICE_URL", () -> url);
        registry.add("BANKING_SERVICE_URL", () -> url);
        var closedPort = findUnusedPort();
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
    void lesComptesDeDemonstrationSontPublicsEnLecture() throws Exception {
        var response = send(get("/api/auth/demo-accounts"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(LAST_REQUEST.get().path()).isEqualTo("/auth/demo-accounts");
    }

    @Test
    void routeProtegeeAvecJwtValideEstRelayee() throws Exception {
        var response =
                send(
                        get("/api/accounts/42")
                                .header("Authorization", formatBearer(createValidToken())));

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
    void unCheminEncodeNeRendPasPubliqueUneRouteProtegee() throws Exception {
        var requests =
                List.of(
                        get("/api/%61ccounts"),
                        get("/api/auth/demo-accounts/../../accounts"),
                        get("/api/auth/demo-accounts/%2E%2E/%2E%2E/accounts"),
                        post("/api/auth/login/..%2F..%2Faccounts"),
                        post("/api/auth/login;/../../accounts"),
                        post("/api/auth//../accounts"));
        for (var request : requests) {
            var response = send(request);

            assertThat(response.statusCode()).isIn(400, 401);
            assertThat(LAST_REQUEST.get()).isNull();
        }
    }

    @Test
    void uneRoutePubliqueEncodeeResteReconnueCommePublique() throws Exception {
        var response = send(get("/api/auth/demo-account%73"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(LAST_REQUEST.get().path()).isEqualTo("/auth/demo-accounts");
    }

    @Test
    void jwtExpireEstRejete() throws Exception {
        var expired = createToken(SIGNING_KEY, ISSUER, AUDIENCE, Instant.now().minusSeconds(600));

        assertThat(
                        send(get("/api/accounts").header("Authorization", formatBearer(expired)))
                                .statusCode())
                .isEqualTo(401);
    }

    @Test
    void jwtSigneParUneAutreCleEstRejete() throws Exception {
        var forged =
                createToken(generateKey("cle-de-test"), ISSUER, AUDIENCE, computeOneHourFromNow());

        assertThat(
                        send(get("/api/accounts").header("Authorization", formatBearer(forged)))
                                .statusCode())
                .isEqualTo(401);
    }

    @Test
    void jwtDUnAutreEmetteurOuPublicEstRejete() throws Exception {
        var otherIssuer =
                createToken(
                        SIGNING_KEY, "https://pirate.example", AUDIENCE, computeOneHourFromNow());
        var otherAudience = createToken(SIGNING_KEY, ISSUER, "autre-api", computeOneHourFromNow());

        assertThat(
                        send(get("/api/accounts")
                                        .header("Authorization", formatBearer(otherIssuer)))
                                .statusCode())
                .isEqualTo(401);
        assertThat(
                        send(get("/api/accounts")
                                        .header("Authorization", formatBearer(otherAudience)))
                                .statusCode())
                .isEqualTo(401);
    }

    @Test
    void lesEnTetesDIdentiteUsurpesSontRemplacesParCeuxDuJwt() throws Exception {
        send(
                get("/api/accounts")
                        .header("Authorization", formatBearer(createValidToken()))
                        .header("X-User-Id", "usurpateur")
                        .header("X-User-Role", "ROLE_ADMIN"));

        assertThat(LAST_REQUEST.get().findHeader("X-User-Id")).isEqualTo(USER_ID);
        assertThat(LAST_REQUEST.get().findHeader("X-User-Role")).isEqualTo("ROLE_CLIENT");
    }

    @Test
    void lesEnTetesDIdentiteUsurpesSontRetiresDesRoutesPubliques() throws Exception {
        send(post("/api/auth/login").header("X-User-Id", "usurpateur").header("X-User-Role", "x"));

        assertThat(LAST_REQUEST.get().findHeader("X-User-Id")).isNull();
        assertThat(LAST_REQUEST.get().findHeader("X-User-Role")).isNull();
    }

    @Test
    void unCorrelationIdEstGenereQuandIlEstAbsent() throws Exception {
        var response = send(post("/api/auth/login"));

        var generated = response.headers().firstValue("X-Correlation-Id").orElseThrow();
        assertThat(generated).hasSize(36);
        assertThat(LAST_REQUEST.get().findHeader("X-Correlation-Id")).isEqualTo(generated);
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
        var allowed = send(buildPreflight("http://localhost:4200"));
        var refused = send(buildPreflight("https://pirate.example"));

        assertThat(allowed.statusCode()).isEqualTo(200);
        assertThat(allowed.headers().firstValue("Access-Control-Allow-Origin"))
                .hasValue("http://localhost:4200");
        assertThat(allowed.headers().firstValue("Access-Control-Allow-Credentials"))
                .hasValue("true");
        assertThat(refused.statusCode()).isEqualTo(403);
        assertThat(refused.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
    }

    @Test
    void uneOrigineEtrangereNePeutPasUtiliserLeCookieDeRefresh() throws Exception {
        for (var path : List.of("/api/auth/refresh", "/api/auth/logout")) {
            var response =
                    send(
                            post(path)
                                    .header("Origin", "https://pirate.example")
                                    .header("Cookie", "bankflow_refresh=valeur"));

            assertThat(response.statusCode()).isEqualTo(403);
            assertThat(LAST_REQUEST.get()).isNull();
        }
    }

    @Test
    void uneEcritureSurUneRouteInconnueRepond404() throws Exception {
        var response =
                send(
                        post("/route-inconnue")
                                .header("Authorization", formatBearer(createValidToken())));

        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    void unServiceIndisponibleRepond503EnProblemDetail() throws Exception {
        var response =
                send(
                        get("/api/customers/me")
                                .header("Authorization", formatBearer(createValidToken())));

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body()).contains("\"status\":503");
    }

    @Test
    void uneRouteInconnueRepond404EnProblemDetail() throws Exception {
        var response =
                send(get("/api/inconnu").header("Authorization", formatBearer(createValidToken())));

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("\"status\":404");
    }

    @Test
    void laSondeDeSanteResteAccessibleSansJwt() throws Exception {
        assertThat(send(get("/actuator/health/readiness")).statusCode()).isEqualTo(200);
    }

    private HttpRequest.Builder get(String path) {
        return HttpRequest.newBuilder(buildUri(path)).GET();
    }

    private HttpRequest.Builder post(String path) {
        return HttpRequest.newBuilder(buildUri(path))
                .POST(HttpRequest.BodyPublishers.ofString("{}"));
    }

    private HttpRequest.Builder buildPreflight(String origin) {
        return HttpRequest.newBuilder(buildUri("/api/accounts"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "authorization");
    }

    private URI buildUri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String formatBearer(String token) {
        return "Bearer " + token;
    }

    private static String createValidToken() throws Exception {
        return createToken(SIGNING_KEY, ISSUER, AUDIENCE, computeOneHourFromNow());
    }

    private static Instant computeOneHourFromNow() {
        return Instant.now().plusSeconds(3600);
    }

    private static String createToken(RSAKey key, String issuer, String audience, Instant expiresAt)
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

    private static int findUnusedPort() throws IOException {
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
