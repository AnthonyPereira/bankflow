package com.bankflow.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
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
            "bankflow.rate-limit.login-per-minute=3",
            "bankflow.rate-limit.register-per-minute=1",
            "bankflow.rate-limit.transfers-per-minute=1"
        })
@ActiveProfiles("test")
class RateLimitIntegrationTests {

    private static final AtomicReference<String> LAST_RAW_PATH = new AtomicReference<>();
    private static final HttpServer DOWNSTREAM = startDownstream();

    private final HttpClient client = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    private int port;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add(
                "AUTH_SERVICE_URL", () -> "http://127.0.0.1:" + DOWNSTREAM.getAddress().getPort());
    }

    @AfterAll
    static void stopDownstream() {
        DOWNSTREAM.stop(0);
    }

    @Test
    void auDelaDeLaLimiteLaRequeteEstRefuseeEn429() throws Exception {
        var ip = "203.0.113.10";
        for (int attempt = 0; attempt < 3; attempt++) {
            assertThat(post("/api/auth/login", ip).statusCode()).isEqualTo(200);
        }

        var refused = post("/api/auth/login", ip);

        assertThat(refused.statusCode()).isEqualTo(429);
        assertThat(refused.headers().firstValue("Retry-After")).isPresent();
        assertThat(refused.body()).contains("\"status\":429");
    }

    @Test
    void chaqueAdresseIpASaPropreLimite() throws Exception {
        for (int attempt = 0; attempt < 4; attempt++) {
            post("/api/auth/login", "203.0.113.20");
        }

        assertThat(post("/api/auth/login", "203.0.113.21").statusCode()).isEqualTo(200);
    }

    @Test
    void chaqueRouteASaPropreLimite() throws Exception {
        var ip = "203.0.113.30";

        assertThat(post("/api/auth/register", ip).statusCode()).isEqualTo(200);
        assertThat(post("/api/auth/register", ip).statusCode()).isEqualTo(429);
        assertThat(post("/api/auth/login", ip).statusCode()).isEqualTo(200);
    }

    @Test
    void uneBarreFinaleNeContournePasLaLimite() throws Exception {
        var ip = "203.0.113.40";
        post("/api/auth/register", ip);

        assertThat(post("/api/auth/register/", ip).statusCode()).isEqualTo(429);
    }

    @Test
    void unCaractereEncodeAtteintBienLaRouteDeConnexion() throws Exception {
        var response = post("/api/auth/%6Cogin", "203.0.113.50");

        // Le service d'authentification décode ce chemin en /auth/login
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(LAST_RAW_PATH.get()).isEqualTo("/auth/%6Cogin");
    }

    @Test
    void unCaractereEncodeNeContournePasLaLimiteDeConnexion() throws Exception {
        var ip = "203.0.113.51";
        for (int attempt = 0; attempt < 3; attempt++) {
            post("/api/auth/login", ip);
        }

        for (var path :
                List.of("/api/auth/%6Cogin", "/api/auth/%6c%6F%67%69%6E", "/api/%61uth/login/")) {
            assertThat(post(path, ip).statusCode()).as(path).isEqualTo(429);
        }
    }

    @Test
    void unCheminNonNormaliseNeContournePasLaLimiteDeConnexion() throws Exception {
        var ip = "203.0.113.52";
        for (int attempt = 0; attempt < 3; attempt++) {
            post("/api/auth/login", ip);
        }

        for (var path :
                List.of(
                        "/api//auth/login",
                        "/api/auth/./login",
                        "/api/auth/x/../login",
                        "/api/auth/login;x=1",
                        "/api/auth/%2E/login")) {
            // Le pare-feu de Spring Security les rejette aussi, mais après ce filtre
            assertThat(post(path, ip).statusCode()).as(path).isEqualTo(429);
        }
    }

    @Test
    void unCaractereEncodeNeContournePasLaLimiteDInscription() throws Exception {
        var ip = "203.0.113.53";
        post("/api/auth/register", ip);

        assertThat(post("/api/auth/%72egister", ip).statusCode()).isEqualTo(429);
    }

    @Test
    void unCaractereEncodeNeContournePasLaLimiteDesVirements() throws Exception {
        var ip = "203.0.113.54";
        // Sans JWT, la Gateway répond 401, mais la tentative est comptée avant ce contrôle
        assertThat(post("/api/transfers", ip).statusCode()).isEqualTo(401);

        assertThat(post("/api/%74ransfers", ip).statusCode()).isEqualTo(429);
    }

    private HttpResponse<String> post(String path, String clientIp) throws Exception {
        var request =
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .header("X-Forwarded-For", clientIp)
                        .POST(HttpRequest.BodyPublishers.ofString("{}"))
                        .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpServer startDownstream() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext(
                    "/",
                    exchange -> {
                        LAST_RAW_PATH.set(exchange.getRequestURI().getRawPath());
                        exchange.getRequestBody().readAllBytes();
                        exchange.sendResponseHeaders(200, -1);
                        exchange.close();
                    });
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
