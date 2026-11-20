package com.bankflow.auth.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.bankflow.auth.AuthApiTestSupport;
import com.bankflow.auth.domain.user.Role;
import com.bankflow.auth.domain.user.User;
import com.bankflow.auth.domain.user.UserRepository;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

class RegisterIntegrationTests extends AuthApiTestSupport {

    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void inscriptionCreeUnClientAvecUnMotDePasseHache() throws Exception {
        var email = uniqueEmail();

        var response = register(email, VALID_PASSWORD);

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(json(response).get("email").asString()).isEqualTo(email);
        assertThat(json(response).get("role").asString()).isEqualTo("ROLE_CLIENT");
        assertThat(response.body()).doesNotContain("password", VALID_PASSWORD);

        User stored = users.findByEmail(email).orElseThrow();
        assertThat(stored.getRole()).isEqualTo(Role.ROLE_CLIENT);
        assertThat(stored.getPasswordHash()).startsWith("{bcrypt}");
        assertThat(passwordEncoder.matches(VALID_PASSWORD, stored.getPasswordHash())).isTrue();
    }

    @Test
    void unEmailDupliqueEstRefuseQuelleQueSoitSaCasse() throws Exception {
        var email = uniqueEmail();
        register(email, VALID_PASSWORD);

        var response = register(email.toUpperCase(Locale.ROOT), VALID_PASSWORD);

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow())
                .contains("application/problem+json");
    }

    @Test
    void unEmailInvalideEstRefuse() throws Exception {
        var response = register("pas-un-email", VALID_PASSWORD);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(json(response).get("errors").has("email")).isTrue();
    }

    @Test
    void unMotDePasseTropCourtEstRefuse() throws Exception {
        var response = register(uniqueEmail(), "trop-court");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(json(response).get("errors").has("password")).isTrue();
    }

    @Test
    void unMotDePasseDePlusDe72OctetsEstRefuse() throws Exception {
        var response = register(uniqueEmail(), "é".repeat(37));

        assertThat(response.statusCode()).isEqualTo(400);
    }
}
