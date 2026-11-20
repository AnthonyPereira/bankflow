package com.bankflow.auth.api.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bankflow.auth.AuthApiTestSupport;
import com.bankflow.auth.domain.user.Role;
import com.bankflow.auth.domain.user.User;
import com.bankflow.auth.domain.user.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

class DemoAccountsIntegrationTests extends AuthApiTestSupport {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void listeLesComptesDeDemonstrationSansAuthentification() throws Exception {
        var response = send(buildRequest("/auth/demo-accounts").GET());

        assertThat(response.statusCode()).isEqualTo(200);
        var accounts = parseJson(response);
        assertThat(accounts).hasSize(2);
        assertThat(accounts.get(0).get("email").asString()).isEqualTo("demo.client@bankflow.local");
        assertThat(accounts.get(0).get("role").asString()).isEqualTo("ROLE_CLIENT");
        assertThat(accounts.get(1).get("role").asString()).isEqualTo("ROLE_ADMIN");
    }

    @Test
    void lesComptesDeDemonstrationSontMarquesCommeTelsEnBase() {
        var demo =
                jdbcTemplate.queryForList(
                        // Les tests créent aussi leurs propres comptes de démonstration
                        "SELECT email FROM users WHERE demo AND email LIKE '%@bankflow.local'"
                                + " ORDER BY email",
                        String.class);

        assertThat(demo).containsExactly("demo.admin@bankflow.local", "demo.client@bankflow.local");
    }

    @Test
    void laBaseRefuseDeDesactiverUnCompteDeDemonstration() {
        var email = "demo-" + UUID.randomUUID() + "@bankflow.test";
        users.save(
                User.registerDemo(
                        email,
                        passwordEncoder.encode("un-mot-de-passe-solide"),
                        Role.ROLE_CLIENT,
                        Instant.now()));
        try {
            assertThatThrownBy(() ->
                            jdbcTemplate.update(
                                    "UPDATE users SET enabled = FALSE WHERE email = ?", email))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("users_demo_always_enabled");
            assertThat(
                            jdbcTemplate.queryForObject(
                                    "SELECT enabled FROM users WHERE email = ?",
                                    Boolean.class,
                                    email))
                    .isTrue();
        } finally {
            jdbcTemplate.update("DELETE FROM users WHERE email = ?", email);
        }
    }

    @Test
    void chaqueCompteAfficheePermetDeSeConnecter() throws Exception {
        for (var account : parseJson(send(buildRequest("/auth/demo-accounts").GET()))) {
            var login =
                    postJson(
                            "/auth/login",
                            buildCredentials(
                                    account.get("email").asString(),
                                    account.get("password").asString()));

            assertThat(login.statusCode()).isEqualTo(200);
        }
    }
}
