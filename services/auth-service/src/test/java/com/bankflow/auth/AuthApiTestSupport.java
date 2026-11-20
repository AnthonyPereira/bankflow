package com.bankflow.auth;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Socle des tests d'intégration : un vrai serveur HTTP, PostgreSQL et Kafka en conteneurs. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AuthApiTestSupport {

    protected static final String VALID_PASSWORD = "un-mot-de-passe-solide";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final HttpClient client = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    private int port;

    protected static String uniqueEmail() {
        return "utilisateur-" + UUID.randomUUID() + "@bankflow.test";
    }

    protected static JsonNode json(HttpResponse<String> response) {
        return JSON.readTree(response.body());
    }

    protected static String credentials(String email, String password) {
        return JSON.writeValueAsString(java.util.Map.of("email", email, "password", password));
    }

    protected HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
    }

    protected HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> postJson(String path, String body) throws Exception {
        return send(
                request(path)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    protected HttpResponse<String> register(String email, String password) throws Exception {
        return postJson("/auth/register", credentials(email, password));
    }
}
