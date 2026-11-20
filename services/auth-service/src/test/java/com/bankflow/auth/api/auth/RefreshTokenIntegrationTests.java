package com.bankflow.auth.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.bankflow.auth.AuthApiTestSupport;
import com.bankflow.auth.application.token.RefreshTokenService;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class RefreshTokenIntegrationTests extends AuthApiTestSupport {

    private static final String COOKIE = "bankflow_refresh";

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private RefreshTokenService refreshTokenService;

    @Test
    void laConnexionPoseUnCookieHttpOnlyStrictEtNeStockeQueLEmpreinte() throws Exception {
        var email = generateUniqueEmail();
        var login = registerAndLogin(email);
        var token = extractCookieValue(login);

        var header = readSetCookie(login);
        assertThat(header)
                .contains("HttpOnly", "SameSite=Strict", "Path=/api/auth", "Max-Age=604800");
        assertThat(login.body()).doesNotContain(token);
        // La seule ligne de la session porte l'empreinte SHA-256 du jeton, jamais sa valeur
        assertThat(
                        jdbcTemplate.queryForList(
                                "SELECT token_hash FROM refresh_tokens"
                                        + " WHERE user_id = (SELECT id FROM users WHERE email = ?)",
                                String.class,
                                email))
                .containsExactly(sha256(token));
        assertThat(countTokensWithHash(token)).isZero();
    }

    @Test
    void leRefreshEmetUnNouveauJwtEtFaitTournerLeCookie() throws Exception {
        var login = registerAndLogin();

        var refreshed = refresh(extractCookieValue(login));

        assertThat(refreshed.statusCode()).isEqualTo(200);
        assertThat(parseJson(refreshed).get("accessToken").asString()).isNotBlank();
        assertThat(extractCookieValue(refreshed)).isNotEqualTo(extractCookieValue(login));
        assertThat(readRevokedReason(extractCookieValue(login))).isEqualTo("ROTATION");
    }

    @Test
    void unRefreshSansCookieOuAvecUnJetonInconnuEstRefuse() throws Exception {
        assertThat(send(post("/auth/refresh")).statusCode()).isEqualTo(401);
        assertThat(refresh("jeton-inconnu").statusCode()).isEqualTo(401);
    }

    @Test
    void uneRequeteSimultaneeEstRefuseeSansDeconnecterLUtilisateur() throws Exception {
        var login = registerAndLogin();
        var rotated = refresh(extractCookieValue(login));

        var concurrent = refresh(extractCookieValue(login));

        assertThat(concurrent.statusCode()).isEqualTo(401);
        assertThat(concurrent.headers().firstValue("Set-Cookie")).isEmpty();
        assertThat(refresh(extractCookieValue(rotated)).statusCode()).isEqualTo(200);
    }

    @Test
    void laReutilisationDUnJetonRevoqueToutesLaFamille() throws Exception {
        var email = generateUniqueEmail();
        var login = registerAndLogin(email);
        var rotated = refresh(extractCookieValue(login));
        ageRevocations(email);

        var reused = refresh(extractCookieValue(login));

        assertThat(reused.statusCode()).isEqualTo(401);
        assertThat(readSetCookie(reused)).contains("Max-Age=0");
        assertThat(readRevokedReason(extractCookieValue(rotated))).isEqualTo("REUSE_DETECTED");
        // Révoqué à l'instant pour vol, le jeton courant ne bénéficie d'aucun délai de grâce
        var current = refresh(extractCookieValue(rotated));
        assertThat(current.statusCode()).isEqualTo(401);
        assertThat(readSetCookie(current)).contains("Max-Age=0");
    }

    @Test
    void unJetonPresenteJusteApresLaDeconnexionEstRefuseEtSonCookieEfface() throws Exception {
        var login = registerAndLogin();
        logout(extractCookieValue(login));

        var refused = refresh(extractCookieValue(login));

        assertThat(refused.statusCode()).isEqualTo(401);
        assertThat(readSetCookie(refused)).contains("Max-Age=0");
        assertThat(readRevokedReason(extractCookieValue(login))).isEqualTo("LOGOUT");
    }

    @Test
    void unJetonRemplacePuisDeconnecteNeBeneficiePlusDuDelaiDeGrace() throws Exception {
        var login = registerAndLogin();
        var rotated = refresh(extractCookieValue(login));
        logout(extractCookieValue(rotated));

        // Le jeton remplacé l'a été il y a moins de 10 secondes, mais la session est terminée
        var refused = refresh(extractCookieValue(login));

        assertThat(refused.statusCode()).isEqualTo(401);
        assertThat(readSetCookie(refused)).contains("Max-Age=0");
    }

    @Test
    void unJetonExpireEstRefuse() throws Exception {
        var email = generateUniqueEmail();
        var login = registerAndLogin(email);
        jdbcTemplate.update(
                "UPDATE refresh_tokens SET expires_at = now() - interval '1 minute'"
                        + " WHERE user_id = (SELECT id FROM users WHERE email = ?)",
                email);

        var refused = refresh(extractCookieValue(login));

        assertThat(refused.statusCode()).isEqualTo(401);
        assertThat(readSetCookie(refused)).contains("Max-Age=0");
    }

    @Test
    void uneSessionOuverteIlYAPlusDeTrenteJoursEstRefuseeEtSonCookieEfface() throws Exception {
        var email = generateUniqueEmail();
        var login = registerAndLogin(email);
        var rotated = refresh(extractCookieValue(login));
        // Le jeton courant reste valable : seule la date d'ouverture de la session a vieilli
        jdbcTemplate.update(
                "UPDATE refresh_tokens SET family_created_at = now() - interval '31 days'"
                        + " WHERE user_id = (SELECT id FROM users WHERE email = ?)",
                email);

        var refused = refresh(extractCookieValue(rotated));

        assertThat(refused.statusCode()).isEqualTo(401);
        assertThat(readSetCookie(refused)).contains("Max-Age=0");
    }

    @Test
    void uneRotationConserveLaDateDOuvertureDeLaSession() throws Exception {
        var email = generateUniqueEmail();
        var login = registerAndLogin(email);

        refresh(extractCookieValue(refresh(extractCookieValue(login))));

        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT count(DISTINCT family_created_at) FROM refresh_tokens"
                                        + " WHERE user_id = (SELECT id FROM users WHERE email = ?)",
                                Integer.class,
                                email))
                .isEqualTo(1);
    }

    @Test
    void laPurgeSupprimeLesJetonsExpiresNonRevoques() throws Exception {
        var expiredEmail = generateUniqueEmail();
        var activeEmail = generateUniqueEmail();
        registerAndLogin(expiredEmail);
        registerAndLogin(activeEmail);
        jdbcTemplate.update(
                "UPDATE refresh_tokens SET expires_at = now() - interval '1 minute'"
                        + " WHERE user_id = (SELECT id FROM users WHERE email = ?)",
                expiredEmail);

        refreshTokenService.purgeExpired();

        assertThat(countRefreshTokens(expiredEmail)).isZero();
        assertThat(countRefreshTokens(activeEmail)).isEqualTo(1);
    }

    @Test
    void unJetonRevoqueEtExpireSurvitALaPurgeTantQueSaFamillePeutEtreActive() throws Exception {
        var email = generateUniqueEmail();
        var login = registerAndLogin(email);
        var rotated = refresh(extractCookieValue(login));
        // Le jeton remplacé a expiré, mais la session n'a que quelques jours
        jdbcTemplate.update(
                "UPDATE refresh_tokens SET expires_at = now() - interval '1 minute',"
                        + " revoked_at = now() - interval '8 days',"
                        + " family_created_at = now() - interval '15 days'"
                        + " WHERE token_hash = ?",
                sha256(extractCookieValue(login)));

        refreshTokenService.purgeExpired();

        assertThat(countTokensWithHash(sha256(extractCookieValue(login)))).isEqualTo(1);
        var reused = refresh(extractCookieValue(login));
        assertThat(reused.statusCode()).isEqualTo(401);
        assertThat(readSetCookie(reused)).contains("Max-Age=0");
        assertThat(refresh(extractCookieValue(rotated)).statusCode()).isEqualTo(401);
    }

    @Test
    void laPurgeSupprimeLesFamillesQuiOntDepasseLaDureeMaximaleDeSession() throws Exception {
        var email = generateUniqueEmail();
        var login = registerAndLogin(email);
        refresh(extractCookieValue(login));
        jdbcTemplate.update(
                "UPDATE refresh_tokens SET family_created_at = now() - interval '31 days'"
                        + " WHERE user_id = (SELECT id FROM users WHERE email = ?)",
                email);

        refreshTokenService.purgeExpired();

        assertThat(countRefreshTokens(email)).isZero();
    }

    @Test
    void unCompteDesactiveNePeutPlusRafraichirSaSession() throws Exception {
        var email = generateUniqueEmail();
        var login = registerAndLogin(email);
        jdbcTemplate.update("UPDATE users SET enabled = FALSE WHERE email = ?", email);

        var refused = refresh(extractCookieValue(login));

        assertThat(refused.statusCode()).isEqualTo(401);
        assertThat(readSetCookie(refused)).contains("Max-Age=0");
        assertThat(readRevokedReason(extractCookieValue(login))).isEqualTo("ACCOUNT_DISABLED");
    }

    @Test
    void laDeconnexionRevoqueLaSessionEtEffaceLeCookie() throws Exception {
        var login = registerAndLogin();

        var logout = logout(extractCookieValue(login));

        assertThat(logout.statusCode()).isEqualTo(204);
        assertThat(readSetCookie(logout)).contains("Max-Age=0");
        assertThat(refresh(extractCookieValue(login)).statusCode()).isEqualTo(401);
    }

    @Test
    void laDeconnexionSansCookieReussit() throws Exception {
        assertThat(send(post("/auth/logout")).statusCode()).isEqualTo(204);
    }

    private HttpResponse<String> registerAndLogin() throws Exception {
        return registerAndLogin(generateUniqueEmail());
    }

    private HttpResponse<String> registerAndLogin(String email) throws Exception {
        register(email, VALID_PASSWORD);
        return postJson("/auth/login", buildCredentials(email, VALID_PASSWORD));
    }

    /** Fait passer les révocations de cet utilisateur, et de lui seul, hors du délai de grâce. */
    private void ageRevocations(String email) {
        jdbcTemplate.update(
                "UPDATE refresh_tokens SET revoked_at = now() - interval '1 minute'"
                        + " WHERE revoked_at IS NOT NULL"
                        + " AND user_id = (SELECT id FROM users WHERE email = ?)",
                email);
    }

    private int countRefreshTokens(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refresh_tokens"
                        + " WHERE user_id = (SELECT id FROM users WHERE email = ?)",
                Integer.class,
                email);
    }

    private int countTokensWithHash(String tokenHash) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE token_hash = ?",
                Integer.class,
                tokenHash);
    }

    private String readRevokedReason(String token) {
        return jdbcTemplate.queryForObject(
                "SELECT revoked_reason FROM refresh_tokens WHERE token_hash = ?",
                String.class,
                sha256(token));
    }

    private HttpResponse<String> refresh(String token) throws Exception {
        return send(post("/auth/refresh").header("Cookie", COOKIE + "=" + token));
    }

    private HttpResponse<String> logout(String token) throws Exception {
        return send(post("/auth/logout").header("Cookie", COOKIE + "=" + token));
    }

    private HttpRequest.Builder post(String path) {
        return buildRequest(path).POST(HttpRequest.BodyPublishers.noBody());
    }

    private static String readSetCookie(HttpResponse<String> response) {
        return response.headers().firstValue("Set-Cookie").orElseThrow();
    }

    private static String extractCookieValue(HttpResponse<String> response) {
        var header = readSetCookie(response);
        return header.substring(COOKIE.length() + 1, header.indexOf(';'));
    }

    /** Même encodage que RefreshTokenService : SHA-256 de l'UTF-8, en hexadécimal minuscule. */
    private static String sha256(String rawToken) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
