package com.bankflow.customer;

import com.bankflow.customer.application.customer.CustomerRegistrationService;
import com.bankflow.customer.domain.customer.Customer;
import com.bankflow.customer.domain.customer.CustomerRepository;
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Socle des tests d'intégration : un vrai serveur HTTP, PostgreSQL et Kafka en conteneurs, et un
 * serveur JWKS local qui permet de signer des JWT de test.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class CustomerApiTestSupport {

    protected static final String ROLE_CLIENT = "ROLE_CLIENT";
    protected static final String ROLE_ADMIN = "ROLE_ADMIN";

    private static final String ISSUER = "https://bankflow.local/auth";
    private static final String AUDIENCE = "bankflow-api";
    private static final Duration KAFKA_TIMEOUT = Duration.ofSeconds(30);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final RSAKey KEY = generateKey();
    private static HttpServer jwks;
    private static int jwksPort;

    private final HttpClient client = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    private int port;

    @Autowired protected CustomerRepository customerRepository;
    @Autowired protected CustomerRegistrationService registrationService;
    @Autowired private KafkaContainer kafka;

    /**
     * Le serveur JWKS est arrêté en fin de classe. Il est relancé sur le même port pour la classe
     * suivante : le contexte Spring, mis en cache, garde la même adresse.
     */
    @BeforeAll
    static synchronized void startJwks() {
        if (jwks == null) {
            jwks = startJwksServer(jwksPort);
            jwksPort = jwks.getAddress().getPort();
        }
    }

    @AfterAll
    static synchronized void stopJwks() {
        if (jwks != null) {
            jwks.stop(0);
            jwks = null;
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("JWK_SET_URI", () -> "http://127.0.0.1:" + jwksPort + "/jwks.json");
    }

    protected static String generateUniqueEmail() {
        return "client-" + UUID.randomUUID() + "@bankflow.test";
    }

    protected static JsonNode parseJson(String body) {
        return JSON.readTree(body);
    }

    protected static JsonNode parseJson(HttpResponse<String> response) {
        return JSON.readTree(response.body());
    }

    /**
     * Crée un client sans passer par Kafka : le traitement est le même que celui du consommateur.
     */
    protected Customer createCustomer(String email, boolean demo) {
        var userId = UUID.randomUUID();
        registrationService.registerUser(userId, email, demo);
        return customerRepository.findByUserId(userId).orElseThrow();
    }

    protected HttpResponse<String> get(String path, String token) throws Exception {
        return send(buildRequest(path, token).GET());
    }

    protected HttpResponse<String> post(String path, String token, String body) throws Exception {
        return send(
                buildRequest(path, token)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    protected HttpResponse<String> put(String path, String token, String body) throws Exception {
        return send(
                buildRequest(path, token)
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(body)));
    }

    protected static String createToken(UUID userId, String role, boolean demo) throws Exception {
        return createToken(userId.toString(), ISSUER, AUDIENCE, role, demo);
    }

    protected static String createToken(UUID userId, String role) throws Exception {
        return createToken(userId.toString(), ISSUER, AUDIENCE, role);
    }

    protected static String createToken(String subject, String issuer, String audience, String role)
            throws Exception {
        return createToken(subject, issuer, audience, role, false);
    }

    protected static String createToken(
            String subject, String issuer, String audience, String role, boolean demo)
            throws Exception {
        var claims =
                new JWTClaimsSet.Builder()
                        .subject(subject)
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
    }

    /** Attend au moins {@code minimum} enveloppes de ce type pour cette clé (attente bornée). */
    protected List<JsonNode> awaitEvents(String topic, String key, String eventType, int minimum) {
        var found = new ArrayList<JsonNode>();
        try (var consumer = createConsumer(topic)) {
            var deadline = System.nanoTime() + KAFKA_TIMEOUT.toNanos();
            while (System.nanoTime() < deadline && found.size() < minimum) {
                collect(consumer, key, eventType, found, Duration.ofMillis(500));
            }
        }
        if (found.size() < minimum) {
            throw new AssertionError(
                    eventType
                            + " attendu "
                            + minimum
                            + " fois pour "
                            + key
                            + ", reçu "
                            + found.size());
        }
        return found;
    }

    /**
     * Lit le topic pendant toute la durée donnée : pour prouver qu'un événement n'est pas publié.
     */
    protected List<JsonNode> collectEventsDuring(
            String topic, String key, String eventType, Duration duration) {
        var found = new ArrayList<JsonNode>();
        try (var consumer = createConsumer(topic)) {
            var deadline = System.nanoTime() + duration.toNanos();
            while (System.nanoTime() < deadline) {
                collect(consumer, key, eventType, found, Duration.ofMillis(500));
            }
        }
        return found;
    }

    /** Attend qu'un client existe pour cet utilisateur (attente bornée). */
    protected Customer awaitCustomer(UUID userId) throws InterruptedException {
        var deadline = System.nanoTime() + KAFKA_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            var customer = customerRepository.findByUserId(userId);
            if (customer.isPresent()) {
                return customer.get();
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Aucun client créé pour " + userId);
    }

    private static void collect(
            KafkaConsumer<String, String> consumer,
            String key,
            String eventType,
            List<JsonNode> found,
            Duration pollTimeout) {
        for (var record : consumer.poll(pollTimeout)) {
            if (key.equals(record.key())) {
                var envelope = JSON.readTree(record.value());
                if (eventType.equals(envelope.path("eventType").asString())) {
                    found.add(envelope);
                }
            }
        }
    }

    private KafkaConsumer<String, String> createConsumer(String topic) {
        var consumer =
                new KafkaConsumer<>(
                        Map.<String, Object>of(
                                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                                kafka.getBootstrapServers(),
                                ConsumerConfig.GROUP_ID_CONFIG,
                                "test-" + UUID.randomUUID(),
                                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                                "earliest"),
                        new StringDeserializer(),
                        new StringDeserializer());
        consumer.subscribe(List.of(topic));
        return consumer;
    }

    private HttpRequest.Builder buildRequest(String path, String token) {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return request;
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static RSAKey generateKey() {
        try {
            return new RSAKeyGenerator(2048).keyID("cle-de-test").generate();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static HttpServer startJwksServer(int port) {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
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
