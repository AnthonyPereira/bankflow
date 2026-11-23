package com.bankflow.banking;

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
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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
    private static final HttpServer JWKS = startJwksServer();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    protected final HttpClient client = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    private int port;

    @Autowired protected CustomerRefRepository customerRefs;

    @DynamicPropertySource
    static void registerJwksProperty(DynamicPropertyRegistry registry) {
        registry.add(
                "JWK_SET_URI",
                () -> "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/jwks.json");
    }

    /** Crée la référence d'un client, comme le ferait le consommateur Kafka. */
    protected CustomerRef createCustomerRef(UUID userId, CustomerStatus status) {
        return customerRefs.saveAndFlush(
                new CustomerRef(
                        UUID.randomUUID(),
                        userId,
                        userId + "@bankflow.test",
                        status,
                        Instant.now()));
    }

    protected HttpResponse<String> send(String method, String path, String token, String body)
            throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
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
        try {
            var claims =
                    new JWTClaimsSet.Builder()
                            .subject(userId.toString())
                            .issuer(ISSUER)
                            .audience(AUDIENCE)
                            .claim("role", role)
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
