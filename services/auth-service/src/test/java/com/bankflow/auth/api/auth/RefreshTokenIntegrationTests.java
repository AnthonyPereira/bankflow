package com.bankflow.auth.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.bankflow.auth.AuthApiTestSupport;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class RefreshTokenIntegrationTests extends AuthApiTestSupport {

    private static final String COOKIE = "bankflow_refresh";

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void laConnexionPoseUnCookieHttpOnlyStrictEtNeStockeQueLEmpreinte() throws Exception {
        var login = registerAndLogin();

        var header = setCookie(login);
        assertThat(header)
                .contains("HttpOnly", "SameSite=Strict", "Path=/api/auth", "Max-Age=604800");
        assertThat(login.body()).doesNotContain(cookieValue(login));
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT count(*) FROM refresh_tokens WHERE token_hash = ?",
                                Integer.class,
                                cookieValue(login)))
                .isZero();
    }

    @Test
    void leRefreshEmetUnNouveauJwtEtFaitTournerLeCookie() throws Exception {
        var login = registerAndLogin();

        var refreshed = refresh(cookieValue(login));

        assertThat(refreshed.statusCode()).isEqualTo(200);
        assertThat(json(refreshed).get("accessToken").asString()).isNotBlank();
        assertThat(cookieValue(refreshed)).isNotEqualTo(cookieValue(login));
    }

    @Test
    void unRefreshSansCookieOuAvecUnJetonInconnuEstRefuse() throws Exception {
        assertThat(send(post("/auth/refresh")).statusCode()).isEqualTo(401);
        assertThat(refresh("jeton-inconnu").statusCode()).isEqualTo(401);
    }

    @Test
    void uneRequeteSimultaneeEstRefuseeSansDeconnecterLUtilisateur() throws Exception {
        var login = registerAndLogin();
        var rotated = refresh(cookieValue(login));

        var concurrent = refresh(cookieValue(login));

        assertThat(concurrent.statusCode()).isEqualTo(401);
        assertThat(concurrent.headers().firstValue("Set-Cookie")).isEmpty();
        assertThat(refresh(cookieValue(rotated)).statusCode()).isEqualTo(200);
    }

    @Test
    void laReutilisationDUnJetonRevoqueToutesLaFamille() throws Exception {
        var login = registerAndLogin();
        var rotated = refresh(cookieValue(login));
        jdbcTemplate.update(
                "UPDATE refresh_tokens SET revoked_at = now() - interval '1 minute'"
                        + " WHERE revoked_at IS NOT NULL");

        var reused = refresh(cookieValue(login));

        assertThat(reused.statusCode()).isEqualTo(401);
        assertThat(setCookie(reused)).contains("Max-Age=0");
        assertThat(refresh(cookieValue(rotated)).statusCode()).isEqualTo(401);
    }

    @Test
    void unJetonExpireEstRefuse() throws Exception {
        var email = uniqueEmail();
        var login = registerAndLogin(email);
        jdbcTemplate.update(
                "UPDATE refresh_tokens SET expires_at = now() - interval '1 minute'"
                        + " WHERE user_id = (SELECT id FROM users WHERE email = ?)",
                email);

        assertThat(refresh(cookieValue(login)).statusCode()).isEqualTo(401);
    }

    @Test
    void unCompteDesactiveNePeutPlusRafraichirSaSession() throws Exception {
        var email = uniqueEmail();
        var login = registerAndLogin(email);
        jdbcTemplate.update("UPDATE users SET enabled = FALSE WHERE email = ?", email);

        assertThat(refresh(cookieValue(login)).statusCode()).isEqualTo(401);
    }

    @Test
    void laDeconnexionRevoqueLaSessionEtEffaceLeCookie() throws Exception {
        var login = registerAndLogin();

        var logout = send(post("/auth/logout").header("Cookie", COOKIE + "=" + cookieValue(login)));

        assertThat(logout.statusCode()).isEqualTo(204);
        assertThat(setCookie(logout)).contains("Max-Age=0");
        assertThat(refresh(cookieValue(login)).statusCode()).isEqualTo(401);
    }

    @Test
    void laDeconnexionSansCookieReussit() throws Exception {
        assertThat(send(post("/auth/logout")).statusCode()).isEqualTo(204);
    }

    private HttpResponse<String> registerAndLogin() throws Exception {
        return registerAndLogin(uniqueEmail());
    }

    private HttpResponse<String> registerAndLogin(String email) throws Exception {
        register(email, VALID_PASSWORD);
        return postJson("/auth/login", credentials(email, VALID_PASSWORD));
    }

    private HttpResponse<String> refresh(String token) throws Exception {
        return send(post("/auth/refresh").header("Cookie", COOKIE + "=" + token));
    }

    private HttpRequest.Builder post(String path) {
        return request(path).POST(HttpRequest.BodyPublishers.noBody());
    }

    private static String setCookie(HttpResponse<String> response) {
        return response.headers().firstValue("Set-Cookie").orElseThrow();
    }

    private static String cookieValue(HttpResponse<String> response) {
        var header = setCookie(response);
        return header.substring(COOKIE.length() + 1, header.indexOf(';'));
    }
}
