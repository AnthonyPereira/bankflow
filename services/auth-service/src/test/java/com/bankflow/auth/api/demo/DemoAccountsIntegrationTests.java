package com.bankflow.auth.api.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.bankflow.auth.AuthApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class DemoAccountsIntegrationTests extends AuthApiTestSupport {

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void listeLesComptesDeDemonstrationSansAuthentification() throws Exception {
        var response = send(request("/auth/demo-accounts").GET());

        assertThat(response.statusCode()).isEqualTo(200);
        var accounts = json(response);
        assertThat(accounts).hasSize(2);
        assertThat(accounts.get(0).get("email").asString()).isEqualTo("demo.client@bankflow.local");
        assertThat(accounts.get(0).get("role").asString()).isEqualTo("ROLE_CLIENT");
        assertThat(accounts.get(1).get("role").asString()).isEqualTo("ROLE_ADMIN");
    }

    @Test
    void lesComptesDeDemonstrationSontMarquesCommeTelsEnBase() {
        var demo =
                jdbcTemplate.queryForList(
                        "SELECT email FROM users WHERE demo ORDER BY email", String.class);

        assertThat(demo).containsExactly("demo.admin@bankflow.local", "demo.client@bankflow.local");
    }

    @Test
    void chaqueCompteAfficheePermetDeSeConnecter() throws Exception {
        for (var account : json(send(request("/auth/demo-accounts").GET()))) {
            var login =
                    postJson(
                            "/auth/login",
                            credentials(
                                    account.get("email").asString(),
                                    account.get("password").asString()));

            assertThat(login.statusCode()).isEqualTo(200);
        }
    }
}
