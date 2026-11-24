package com.bankflow.banking;

import static org.assertj.core.api.Assertions.assertThat;

import com.bankflow.banking.domain.customer.CustomerRef;
import com.bankflow.banking.domain.customer.CustomerRefRepository;
import com.bankflow.banking.domain.customer.CustomerStatus;
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
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Socle des tests d'API : JWT signés, serveur JWKS local et client HTTP. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class BankingApiTestSupport {

    protected static final String ISSUER = "https://bankflow.local/auth";
    protected static final String AUDIENCE = "bankflow-api";
    protected static final String CLIENT = "ROLE_CLIENT";
    protected static final String ADMIN = "ROLE_ADMIN";

    private static final RSAKey KEY = generateKey();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /**
     * Le port est conservé d'une classe à l'autre : les contextes Spring en cache l'ont mémorisé.
     */
    private static int jwksPort;

    private static HttpServer jwks;

    protected final HttpClient client = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    private int port;

    @Autowired protected CustomerRefRepository customerRefs;

    @BeforeAll
    static synchronized void startJwksServer() {
        jwks = createJwksServer(jwksPort);
        jwksPort = jwks.getAddress().getPort();
    }

    @AfterAll
    static synchronized void stopJwksServer() {
        jwks.stop(0);
    }

    @DynamicPropertySource
    static void registerJwksProperty(DynamicPropertyRegistry registry) {
        registry.add("JWK_SET_URI", () -> "http://127.0.0.1:" + jwksPort + "/jwks.json");
    }

    /** Crée la référence d'un client, comme le ferait le consommateur Kafka. */
    protected CustomerRef createCustomerRef(UUID userId, CustomerStatus status) {
        return createCustomerRef(userId, status, false);
    }

    /** Variante pour un client de démonstration ({@code demo} vrai). */
    protected CustomerRef createCustomerRef(UUID userId, CustomerStatus status, boolean demo) {
        return customerRefs.saveAndFlush(
                new CustomerRef(
                        UUID.randomUUID(),
                        userId,
                        userId + "@bankflow.test",
                        status,
                        demo,
                        Instant.now()));
    }

    protected HttpResponse<String> send(String method, String path, String token, String body)
            throws Exception {
        return send(method, path, token, body, Map.of());
    }

    /** Variante avec des en-têtes en plus ; un {@code Content-Type} fourni remplace le JSON. */
    protected HttpResponse<String> send(
            String method, String path, String token, String body, Map<String, String> headers)
            throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (body != null && !headers.containsKey("Content-Type")) {
            request.header("Content-Type", "application/json");
        }
        headers.forEach(request::header);
        request.method(
                method,
                body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    protected JsonNode parseJson(HttpResponse<String> response) {
        return JSON.readTree(response.body());
    }

    protected static String createToken(UUID userId, String role) {
        return createToken(userId, role, ISSUER, AUDIENCE);
    }

    /** Jeton d'un compte de démonstration : le claim {@code demo} est vrai. */
    protected static String createDemoToken(UUID userId, String role) {
        return createToken(userId, role, ISSUER, AUDIENCE, true);
    }

    /** Jeton avec émetteur et audience au choix, pour tester les refus ; rôle nul = sans rôle. */
    protected static String createToken(UUID userId, String role, String issuer, String audience) {
        return createToken(userId, role, issuer, audience, false);
    }

    private static String createToken(
            UUID userId, String role, String issuer, String audience, boolean demo) {
        try {
            var claims =
                    new JWTClaimsSet.Builder()
                            .subject(userId.toString())
                            .issuer(issuer)
                            .audience(audience)
                            .claim("role", role)
                            .claim("demo", demo)
                            .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                            .build();
            var jwt =
                    new SignedJWT(
                            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.getKeyID()).build(),
                            claims);
            jwt.sign(new RSASSASigner(KEY));
            return jwt.serialize();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static RSAKey generateKey() {
        try {
            return new RSAKeyGenerator(2048).keyID("cle-de-test").generate();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static HttpServer createJwksServer(int fixedPort) {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", fixedPort), 0);
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

    /** Solde = crédits − débits des écritures du compte ; sur toute la base, débits = crédits. */
    protected static void assertLedgerInvariants(JdbcTemplate jdbc, String... accountIds) {
        var signed = "SUM(CASE direction WHEN 'CREDIT' THEN amount ELSE -amount END)";
        for (var accountId : accountIds) {
            var balance =
                    jdbc.queryForObject(
                            "SELECT balance FROM accounts WHERE id = ?::uuid",
                            BigDecimal.class,
                            accountId);
            var fromEntries =
                    jdbc.queryForObject(
                            "SELECT COALESCE("
                                    + signed
                                    + ", 0) FROM ledger_entries"
                                    + " WHERE account_id = ?::uuid",
                            BigDecimal.class,
                            accountId);
            assertThat(balance).isEqualByComparingTo(fromEntries);
        }
        var gap =
                jdbc.queryForObject(
                        "SELECT COALESCE(" + signed + ", 0) FROM ledger_entries", BigDecimal.class);
        assertThat(gap).isEqualByComparingTo("0");
    }
}
