package com.bankflow.auth.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.bankflow.auth.AuthApiTestSupport;
import com.bankflow.auth.domain.user.Role;
import com.bankflow.auth.domain.user.User;
import com.bankflow.auth.domain.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

class RegisterIntegrationTests extends AuthApiTestSupport {

    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void inscriptionCreeUnClientAvecUnMotDePasseHache() throws Exception {
        var email = generateUniqueEmail();

        var response = register(email, VALID_PASSWORD);

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(parseJson(response).get("email").asString()).isEqualTo(email);
        assertThat(parseJson(response).get("role").asString()).isEqualTo("ROLE_CLIENT");
        assertThat(response.body()).doesNotContain("password", VALID_PASSWORD);

        User stored = users.findByEmail(email).orElseThrow();
        assertThat(stored.getRole()).isEqualTo(Role.ROLE_CLIENT);
        assertThat(stored.getPasswordHash()).startsWith("{bcrypt}");
        assertThat(passwordEncoder.matches(VALID_PASSWORD, stored.getPasswordHash())).isTrue();
    }

    @Test
    void unEmailDupliqueEstRefuseQuelleQueSoitSaCasse() throws Exception {
        var email = generateUniqueEmail();
        register(email, VALID_PASSWORD);

        var response = register(email.toUpperCase(Locale.ROOT), VALID_PASSWORD);

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow())
                .contains("application/problem+json");
    }

    /** RegistrationService ne traduit en 409 que cette contrainte, nommée par PostgreSQL. */
    @Test
    void unDoublonEnBaseSignaleLaContrainteDUniciteDeLEmail() throws Exception {
        var email = generateUniqueEmail();
        register(email, VALID_PASSWORD);
        var duplicate = User.register(email, "{bcrypt}x", Role.ROLE_CLIENT, Instant.now());

        var thrown = catchThrowable(() -> users.saveAndFlush(duplicate));

        assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(thrown.getCause())
                .isInstanceOfSatisfying(
                        ConstraintViolationException.class,
                        violation ->
                                assertThat(violation.getConstraintName())
                                        .isEqualTo("uk_users_email"));
    }

    @Test
    void uneAdresseDeDemonstrationEstRefuseeCommeUnEmailDejaUtilise() throws Exception {
        var email = generateUniqueEmail();
        register(email, VALID_PASSWORD);
        var duplicate = register(email, VALID_PASSWORD);

        for (var demo : List.of("demo.client@bankflow.local", "Demo.Admin@BankFlow.local")) {
            var response = register(demo, VALID_PASSWORD);

            assertThat(response.statusCode()).isEqualTo(409);
            assertThat(parseJson(response).get("detail"))
                    .isEqualTo(parseJson(duplicate).get("detail"));
        }
    }

    @Test
    void unEmailInvalideEstRefuse() throws Exception {
        var response = register("pas-un-email", VALID_PASSWORD);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(parseJson(response).get("errors").has("email")).isTrue();
    }

    @Test
    void unMotDePasseTropCourtEstRefuse() throws Exception {
        var response = register(generateUniqueEmail(), "trop-court");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(parseJson(response).get("errors").has("password")).isTrue();
    }

    @Test
    void unMotDePasseDePlusDe72OctetsEstRefuse() throws Exception {
        // 37 caractères mais 74 octets : seule la règle des 72 octets est enfreinte
        var response = register(generateUniqueEmail(), "é".repeat(37));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(parseJson(response).get("errors").get("password").asString())
                .isEqualTo("Le mot de passe ne doit pas dépasser 72 octets.");
        assertThat(parseJson(response).get("errors").properties()).hasSize(1);
    }

    @Test
    void unMotDePasseDe72OctetsEstAccepte() throws Exception {
        assertThat(register(generateUniqueEmail(), "é".repeat(36)).statusCode()).isEqualTo(201);
    }

    @Test
    void uneAdresseQuiDepasse254CaracteresUneFoisNormaliseeEstRefusee() throws Exception {
        // 254 caractères tels quels, 264 une fois en minuscules : « İ » devient « i̇ »
        var domain =
                "a".repeat(63)
                        + "."
                        + "b".repeat(63)
                        + "."
                        + "c".repeat(63)
                        + "."
                        + "d".repeat(46)
                        + ".test";
        var email = "İ".repeat(10) + "@" + domain;
        assertThat(email).hasSize(254);
        assertThat(User.normalizeEmail(email)).hasSize(264);

        var response = register(email, VALID_PASSWORD);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(parseJson(response).get("errors").get("email").asString())
                .isEqualTo("L'adresse email est trop longue.");
    }
}
