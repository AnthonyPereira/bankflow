package com.bankflow.banking;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Le service protège ses API même si une requête contourne la Gateway. */
@Import(SecurityIntegrationTests.RoleProbeController.class)
class SecurityIntegrationTests extends BankingApiTestSupport {

    private static final UUID USER_ID = UUID.fromString("6f1c2d84-7a51-4e0b-9a36-0a7d5c1e9f22");

    /** Route de test : vérifie que le claim {@code role} devient bien une autorité. */
    @RestController
    static class RoleProbeController {

        @GetMapping("/test/admin")
        @PreAuthorize("hasRole('ADMIN')")
        String readAdminProbe() {
            return "ok";
        }
    }

    @Test
    void laSondeDeSanteEstPublique() throws Exception {
        assertThat(get("/actuator/health/liveness", null).statusCode()).isEqualTo(200);
    }

    @Test
    void uneRouteSansJwtEstRefuseeEnProblemDetail() throws Exception {
        var response = get("/ressource", null);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow())
                .contains("application/problem+json");
    }

    @Test
    void unJwtValideEstAccepte() throws Exception {
        assertThat(get("/ressource", createToken(USER_ID, CLIENT)).statusCode()).isEqualTo(404);
    }

    @Test
    void unJwtDUnAutreEmetteurEstRefuse() throws Exception {
        var token = createToken(USER_ID, CLIENT, "https://pirate.example", AUDIENCE);

        assertThat(get("/ressource", token).statusCode()).isEqualTo(401);
    }

    @Test
    void unJwtDestineAUneAutreApiEstRefuse() throws Exception {
        var token = createToken(USER_ID, CLIENT, ISSUER, "autre-api");

        assertThat(get("/ressource", token).statusCode()).isEqualTo(401);
    }

    @Test
    void leClaimRoleClientNeDonnePasAccesAUneRouteAdmin() throws Exception {
        var response = get("/test/admin", createToken(USER_ID, CLIENT));

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow())
                .contains("application/problem+json");
    }

    @Test
    void leClaimRoleAdminDonneAccesAUneRouteAdmin() throws Exception {
        var response = get("/test/admin", createToken(USER_ID, ADMIN));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("ok");
    }

    @Test
    void unRoleSansPrefixeOuAbsentNeDonnePasAccesAUneRouteAdmin() throws Exception {
        // Le claim est repris tel quel : « ADMIN » ne devient pas ROLE_ADMIN
        assertThat(get("/test/admin", createToken(USER_ID, "ADMIN")).statusCode()).isEqualTo(403);
        assertThat(get("/test/admin", createToken(USER_ID, null)).statusCode()).isEqualTo(403);
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        return send("GET", path, token, null);
    }
}
