package com.bankflow.auth.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.bankflow.auth.AuthApiTestSupport;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

class LoginIntegrationTests extends AuthApiTestSupport {

    @Autowired private JwtDecoder jwtDecoder;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Value("${local.server.port}")
    private int port;

    @Test
    void connexionEmetUnJwtCourtSigneAvecLesBonsClaims() throws Exception {
        var email = uniqueEmail();
        var userId = json(register(email, VALID_PASSWORD)).get("id").asString();

        var response = login(email, VALID_PASSWORD);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json(response).get("tokenType").asString()).isEqualTo("Bearer");
        assertThat(json(response).get("expiresIn").asLong()).isEqualTo(900);

        var jwt = jwtDecoder.decode(accessToken(response));
        assertThat(jwt.getSubject()).isEqualTo(userId);
        assertThat(jwt.getClaimAsString("role")).isEqualTo("ROLE_CLIENT");
        assertThat(jwt.getAudience()).containsExactly("bankflow-api");
        assertThat(jwt.getHeaders()).containsEntry("alg", "RS256");
        assertThat(jwt.getExpiresAt())
                .isCloseTo(Instant.now().plusSeconds(900), within(30, ChronoUnit.SECONDS));
        assertThat(jwt.getClaims().keySet()).doesNotContain("email", "password");
    }

    @Test
    void mauvaisMotDePasseEtCompteInconnuRepondentDeFaconIdentique() throws Exception {
        var email = uniqueEmail();
        register(email, VALID_PASSWORD);

        var wrongPassword = login(email, "mauvais-mot-de-passe");
        var unknownAccount = login(uniqueEmail(), VALID_PASSWORD);

        assertThat(wrongPassword.statusCode()).isEqualTo(401);
        assertThat(unknownAccount.statusCode()).isEqualTo(401);
        assertThat(json(wrongPassword).get("detail")).isEqualTo(json(unknownAccount).get("detail"));
    }

    @Test
    void meRenvoieLUtilisateurConnecte() throws Exception {
        var email = uniqueEmail();
        register(email, VALID_PASSWORD);
        var token = accessToken(login(email, VALID_PASSWORD));

        var response = send(request("/auth/me").header("Authorization", "Bearer " + token));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json(response).get("email").asString()).isEqualTo(email);
    }

    @Test
    void meRefuseUnAppelSansJwtOuAvecUnJwtFalsifie() throws Exception {
        var email = uniqueEmail();
        register(email, VALID_PASSWORD);
        var token = accessToken(login(email, VALID_PASSWORD));
        var tampered = token.substring(0, token.length() - 6) + "AAAAAA";

        assertThat(send(request("/auth/me")).statusCode()).isEqualTo(401);
        assertThat(
                        send(request("/auth/me").header("Authorization", "Bearer " + tampered))
                                .statusCode())
                .isEqualTo(401);
    }

    @Test
    void jwksPublieUniquementLaClePubliqueEtPermetDeVerifierLeJwt() throws Exception {
        var email = uniqueEmail();
        register(email, VALID_PASSWORD);
        var token = accessToken(login(email, VALID_PASSWORD));

        var response = send(request("/.well-known/jwks.json"));

        assertThat(response.statusCode()).isEqualTo(200);
        var key = json(response).get("keys").get(0);
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
        var email = uniqueEmail();
        register(email, VALID_PASSWORD);
        failLogin(email, 5);

        assertThat(login(email, VALID_PASSWORD).statusCode()).isEqualTo(401);
    }

    @Test
    void leCompteRedevientAccessibleQuandLeVerrouExpire() throws Exception {
        var email = uniqueEmail();
        register(email, VALID_PASSWORD);
        failLogin(email, 5);
        jdbcTemplate.update(
                "UPDATE users SET locked_until = now() - interval '1 minute' WHERE email = ?",
                email);

        assertThat(login(email, VALID_PASSWORD).statusCode()).isEqualTo(200);
        assertThat(attemptsOf(email)).isZero();
    }

    @Test
    void uneConnexionReussieRemetLeCompteurDEchecsAZero() throws Exception {
        var email = uniqueEmail();
        register(email, VALID_PASSWORD);
        failLogin(email, 3);
        assertThat(attemptsOf(email)).isEqualTo(3);

        login(email, VALID_PASSWORD);

        assertThat(attemptsOf(email)).isZero();
    }

    private void failLogin(String email, int times) throws Exception {
        for (int attempt = 0; attempt < times; attempt++) {
            assertThat(login(email, "mauvais-mot-de-passe").statusCode()).isEqualTo(401);
        }
    }

    private int attemptsOf(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT failed_login_attempts FROM users WHERE email = ?", Integer.class, email);
    }

    private HttpResponse<String> login(String email, String password) throws Exception {
        return postJson("/auth/login", credentials(email, password));
    }

    private static String accessToken(HttpResponse<String> response) {
        return json(response).get("accessToken").asString();
    }
}
