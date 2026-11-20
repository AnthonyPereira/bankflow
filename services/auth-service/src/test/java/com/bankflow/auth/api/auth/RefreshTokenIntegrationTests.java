package com.bankflow.auth.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.bankflow.auth.AuthApiTestSupport;
import com.bankflow.auth.application.token.RefreshTokenService;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class RefreshTokenIntegrationTests extends AuthApiTestSupport {

    private static final String COOKIE = "bankflow_refresh";

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private RefreshTokenService refreshTokenService;

    @Test
    void laConnexionPoseUnCookieHttpOnlyStrictEtNeStockeQueLEmpreinte() throws Exception {
        var login = registerAndLogin();

        var header = readSetCookie(login);
        assertThat(header)
                .contains("HttpOnly", "SameSite=Strict", "Path=/api/auth", "Max-Age=604800");
        assertThat(login.body()).doesNotContain(extractCookieValue(login));
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT count(*) FROM refresh_tokens WHERE token_hash = ?",
                                Integer.class,
                                extractCookieValue(login)))
                .isZero();
    }

    @Test
    void leRefreshEmetUnNouveauJwtEtFaitTournerLeCookie() throws Exception {
        var login = registerAndLogin();

        var refreshed = refresh(extractCookieValue(login));

        assertThat(refreshed.statusCode()).isEqualTo(200);
        assertThat(parseJson(refreshed).get("accessToken").asString()).isNotBlank();
        assertThat(extractCookieValue(refreshed)).isNotEqualTo(extractCookieValue(login));
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
        var login = registerAndLogin();
        var rotated = refresh(extractCookieValue(login));
        jdbcTemplate.update(
                "UPDATE refresh_tokens SET revoked_at = now() - interval '1 minute'"
                        + " WHERE revoked_at IS NOT NULL");

        var reused = refresh(extractCookieValue(login));

        assertThat(reused.statusCode()).isEqualTo(401);
        assertThat(readSetCookie(reused)).contains("Max-Age=0");
        assertThat(refresh(extractCookieValue(rotated)).statusCode()).isEqualTo(401);
    }

    @Test
    void unJetonExpireEstRefuse() throws Exception {
        var email = generateUniqueEmail();
        var login = registerAndLogin(email);
        jdbcTemplate.update(
                "UPDATE refresh_tokens SET expires_at = now() - interval '1 minute'"
                        + " WHERE user_id = (SELECT id FROM users WHERE email = ?)",
                email);

        assertThat(refresh(extractCookieValue(login)).statusCode()).isEqualTo(401);
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
    void laPurgeSupprimeUniquementLesJetonsExpires() throws Exception {
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
    void unCompteDesactiveNePeutPlusRafraichirSaSession() throws Exception {
        var email = generateUniqueEmail();
        var login = registerAndLogin(email);
        jdbcTemplate.update("UPDATE users SET enabled = FALSE WHERE email = ?", email);

        assertThat(refresh(extractCookieValue(login)).statusCode()).isEqualTo(401);
    }

    @Test
    void laDeconnexionRevoqueLaSessionEtEffaceLeCookie() throws Exception {
        var login = registerAndLogin();

        var logout =
                send(
                        post("/auth/logout")
                                .header("Cookie", COOKIE + "=" + extractCookieValue(login)));

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

    private int countRefreshTokens(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refresh_tokens"
                        + " WHERE user_id = (SELECT id FROM users WHERE email = ?)",
                Integer.class,
                email);
    }

    private HttpResponse<String> refresh(String token) throws Exception {
        return send(post("/auth/refresh").header("Cookie", COOKIE + "=" + token));
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
}
