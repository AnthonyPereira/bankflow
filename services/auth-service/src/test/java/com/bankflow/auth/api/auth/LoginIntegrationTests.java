package com.bankflow.auth.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.bankflow.auth.AuthApiTestSupport;
import com.bankflow.auth.domain.user.Role;
import com.bankflow.auth.domain.user.User;
import com.bankflow.auth.domain.user.UserRepository;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

class LoginIntegrationTests extends AuthApiTestSupport {

    @Autowired private JwtDecoder jwtDecoder;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder passwordEncoder;

    @Value("${local.server.port}")
    private int port;

    @Test
    void connexionEmetUnJwtCourtSigneAvecLesBonsClaims() throws Exception {
        var email = generateUniqueEmail();
        var userId = parseJson(register(email, VALID_PASSWORD)).get("id").asString();

        var response = login(email, VALID_PASSWORD);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(parseJson(response).get("tokenType").asString()).isEqualTo("Bearer");
        assertThat(parseJson(response).get("expiresIn").asLong()).isEqualTo(900);

        var jwt = jwtDecoder.decode(extractAccessToken(response));
        assertThat(jwt.getSubject()).isEqualTo(userId);
        assertThat(jwt.getClaimAsString("role")).isEqualTo("ROLE_CLIENT");
        assertThat(jwt.getAudience()).containsExactly("bankflow-api");
        assertThat(jwt.getHeaders()).containsEntry("alg", "RS256");
        assertThat(jwt.getExpiresAt())
                .isCloseTo(Instant.now().plusSeconds(900), within(30, ChronoUnit.SECONDS));
        assertThat(jwt.getClaims().keySet()).doesNotContain("email", "password");
        assertThat(jwt.getId()).isNotBlank();

        // Le kid du jeton désigne une clé publiée : la Gateway la retrouve dans le JWKS
        var kid = (String) jwt.getHeaders().get("kid");
        var jwks = parseJson(send(buildRequest("/.well-known/jwks.json"))).get("keys");
        assertThat(kid).startsWith("bankflow-1-");
        assertThat(jwks.valueStream().map(key -> key.get("kid").asString())).containsExactly(kid);
    }

    @Test
    void mauvaisMotDePasseEtCompteInconnuRepondentDeFaconIdentique() throws Exception {
        var email = generateUniqueEmail();
        register(email, VALID_PASSWORD);

        var wrongPassword = login(email, "mauvais-mot-de-passe");
        var unknownAccount = login(generateUniqueEmail(), VALID_PASSWORD);

        assertThat(wrongPassword.statusCode()).isEqualTo(401);
        assertThat(unknownAccount.statusCode()).isEqualTo(401);
        assertThat(parseJson(wrongPassword).get("detail"))
                .isEqualTo(parseJson(unknownAccount).get("detail"));
    }

    @Test
    void meRenvoieLUtilisateurConnecte() throws Exception {
        var email = generateUniqueEmail();
        register(email, VALID_PASSWORD);
        var token = extractAccessToken(login(email, VALID_PASSWORD));

        var response = send(buildRequest("/auth/me").header("Authorization", "Bearer " + token));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(parseJson(response).get("email").asString()).isEqualTo(email);
    }

    @Test
    void meRefuseLaSessionDUnCompteDesactiveOuSupprime() throws Exception {
        var disabled = generateUniqueEmail();
        var deleted = generateUniqueEmail();
        register(disabled, VALID_PASSWORD);
        register(deleted, VALID_PASSWORD);
        var disabledToken = extractAccessToken(login(disabled, VALID_PASSWORD));
        var deletedToken = extractAccessToken(login(deleted, VALID_PASSWORD));
        jdbcTemplate.update("UPDATE users SET enabled = FALSE WHERE email = ?", disabled);
        jdbcTemplate.update("DELETE FROM users WHERE email = ?", deleted);

        for (var token : List.of(disabledToken, deletedToken)) {
            var response =
                    send(buildRequest("/auth/me").header("Authorization", "Bearer " + token));

            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(parseJson(response).get("detail").asString())
                    .isEqualTo("Session invalide. Veuillez vous reconnecter.");
        }
    }

    @Test
    void meRefuseUnAppelSansJwtOuAvecUnJwtFalsifie() throws Exception {
        var email = generateUniqueEmail();
        register(email, VALID_PASSWORD);
        var token = extractAccessToken(login(email, VALID_PASSWORD));
        var tampered = token.substring(0, token.length() - 6) + "AAAAAA";

        assertThat(send(buildRequest("/auth/me")).statusCode()).isEqualTo(401);
        assertThat(
                        send(buildRequest("/auth/me").header("Authorization", "Bearer " + tampered))
                                .statusCode())
                .isEqualTo(401);
    }

    @Test
    void jwksPublieUniquementLaClePubliqueEtPermetDeVerifierLeJwt() throws Exception {
        var email = generateUniqueEmail();
        register(email, VALID_PASSWORD);
        var token = extractAccessToken(login(email, VALID_PASSWORD));

        var response = send(buildRequest("/.well-known/jwks.json"));

        assertThat(response.statusCode()).isEqualTo(200);
        var key = parseJson(response).get("keys").get(0);
        assertThat(key.get("kty").asString()).isEqualTo("RSA");
        assertThat(key.has("d")).isFalse();

        var remoteDecoder =
                NimbusJwtDecoder.withJwkSetUri(
                                "http://127.0.0.1:" + port + "/.well-known/jwks.json")
                        .build();
        assertThat(remoteDecoder.decode(token).getSubject()).isNotBlank();
    }

    @Test
    void leCompteEstVerrouilleApresCinqEchecsMemeAvecLeBonMotDePasse() throws Exception {
        var email = generateUniqueEmail();
        register(email, VALID_PASSWORD);
        failLogin(email, 5);

        assertThat(login(email, VALID_PASSWORD).statusCode()).isEqualTo(401);
    }

    @Test
    void leCompteRedevientAccessibleQuandLeVerrouExpire() throws Exception {
        var email = generateUniqueEmail();
        register(email, VALID_PASSWORD);
        failLogin(email, 5);
        jdbcTemplate.update(
                "UPDATE users SET locked_until = now() - interval '1 minute' WHERE email = ?",
                email);

        assertThat(login(email, VALID_PASSWORD).statusCode()).isEqualTo(200);
        assertThat(countFailedAttempts(email)).isZero();
    }

    @Test
    void uneConnexionReussieRemetLeCompteurDEchecsAZero() throws Exception {
        var email = generateUniqueEmail();
        register(email, VALID_PASSWORD);
        failLogin(email, 3);
        assertThat(countFailedAttempts(email)).isEqualTo(3);

        login(email, VALID_PASSWORD);

        assertThat(countFailedAttempts(email)).isZero();
    }

    @Test
    void uneConnexionReussieApresQuatreEchecsRemetLeCompteurEtLeVerrouAZero() throws Exception {
        var email = generateUniqueEmail();
        register(email, VALID_PASSWORD);
        failLogin(email, 4);
        assertThat(countFailedAttempts(email)).isEqualTo(4);

        assertThat(login(email, VALID_PASSWORD).statusCode()).isEqualTo(200);

        assertThat(countFailedAttempts(email)).isZero();
        assertThat(readLockedUntil(email)).isNull();
        // La connexion suivante est réservée à partir d'un compteur revenu à zéro
        assertThat(login(email, VALID_PASSWORD).statusCode()).isEqualTo(200);
        assertThat(countFailedAttempts(email)).isZero();
    }

    @Test
    void unEchecApresExpirationDuVerrouRepartDUnCompteurAUnSansReverrouiller() throws Exception {
        var email = generateUniqueEmail();
        register(email, VALID_PASSWORD);
        failLogin(email, 5);
        jdbcTemplate.update(
                "UPDATE users SET locked_until = now() - interval '1 minute' WHERE email = ?",
                email);
        assertThat(countFailedAttempts(email)).isEqualTo(5);

        assertThat(login(email, "mauvais-mot-de-passe").statusCode()).isEqualTo(401);

        assertThat(countFailedAttempts(email)).isEqualTo(1);
        assertThat(readLockedUntil(email)).isNull();
    }

    @Test
    void desTentativesSimultaneesNeDepassentPasLeNombreDEssaisAutorises() throws Exception {
        var email = generateUniqueEmail();
        register(email, VALID_PASSWORD);
        var start = new CountDownLatch(1);
        var attempts = new ArrayList<Future<Integer>>();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int attempt = 0; attempt < 10; attempt++) {
                attempts.add(
                        executor.submit(() -> {
                            start.await();
                            return login(email, "mauvais-mot-de-passe").statusCode();
                        }));
            }
            start.countDown();
            for (var attempt : attempts) {
                assertThat(attempt.get(60, TimeUnit.SECONDS)).isEqualTo(401);
            }
        }

        // Seules les cinq premières tentatives ont vérifié le vrai mot de passe
        assertThat(countFailedAttempts(email)).isEqualTo(5);
        assertThat(readLockedUntil(email)).isAfter(Instant.now());
        assertThat(login(email, VALID_PASSWORD).statusCode()).isEqualTo(401);
    }

    @Test
    void unCompteDeDemonstrationEstVerrouilleCommeLesAutres() throws Exception {
        // Un compte propre au test : les comptes de démonstration partagés restent intacts
        var email = "demo-" + UUID.randomUUID() + "@bankflow.test";
        users.save(
                User.registerDemo(
                        email,
                        passwordEncoder.encode(VALID_PASSWORD),
                        Role.ROLE_CLIENT,
                        Instant.now()));
        try {
            failLogin(email, 5);

            assertThat(login(email, VALID_PASSWORD).statusCode()).isEqualTo(401);
        } finally {
            jdbcTemplate.update("DELETE FROM users WHERE email = ?", email);
        }
    }

    private Instant readLockedUntil(String email) {
        var lockedUntil =
                jdbcTemplate.queryForObject(
                        "SELECT locked_until FROM users WHERE email = ?", Timestamp.class, email);
        return lockedUntil == null ? null : lockedUntil.toInstant();
    }

    private void failLogin(String email, int times) throws Exception {
        for (int attempt = 0; attempt < times; attempt++) {
            assertThat(login(email, "mauvais-mot-de-passe").statusCode()).isEqualTo(401);
        }
    }

    private int countFailedAttempts(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT failed_login_attempts FROM users WHERE email = ?", Integer.class, email);
    }

    private HttpResponse<String> login(String email, String password) throws Exception {
        return postJson("/auth/login", buildCredentials(email, password));
    }

    private static String extractAccessToken(HttpResponse<String> response) {
        return parseJson(response).get("accessToken").asString();
    }
}
