package com.bankflow.customer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Le service protège ses API même si une requête contourne la Gateway. */
@Import({TestcontainersConfiguration.class, SecurityIntegrationTests.RoleProbeController.class})
class SecurityIntegrationTests extends CustomerApiTestSupport {

    private static final String ISSUER = "https://bankflow.local/auth";
    private static final String AUDIENCE = "bankflow-api";
    private static final String SUBJECT = "6f1c2d84-7a51-4e0b-9a36-0a7d5c1e9f22";

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
        assertThat(parseJson(response).get("code").asString()).isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void unJwtValideEstAccepte() throws Exception {
        assertThat(get("/ressource", createToken(ISSUER, AUDIENCE, ROLE_CLIENT)).statusCode())
                .isEqualTo(404);
    }

    @Test
    void unJwtDUnAutreEmetteurEstRefuse() throws Exception {
        var token = createToken(SUBJECT, "https://pirate.example", AUDIENCE, ROLE_CLIENT);

        assertThat(get("/ressource", token).statusCode()).isEqualTo(401);
    }

    @Test
    void unJwtDestineAUneAutreApiEstRefuse() throws Exception {
        assertThat(get("/ressource", createToken(ISSUER, "autre-api", ROLE_CLIENT)).statusCode())
                .isEqualTo(401);
    }

    @Test
    void leClaimRoleClientNeDonnePasAccesAUneRouteAdmin() throws Exception {
        var response = get("/test/admin", createToken(ISSUER, AUDIENCE, ROLE_CLIENT));

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow())
                .contains("application/problem+json");
        assertThat(parseJson(response).get("code").asString()).isEqualTo("ACCESS_DENIED");
    }

    @Test
    void leClaimRoleAdminDonneAccesAUneRouteAdmin() throws Exception {
        var response = get("/test/admin", createToken(ISSUER, AUDIENCE, ROLE_ADMIN));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("ok");
    }

    @Test
    void unRoleSansPrefixeOuAbsentNeDonnePasAccesAUneRouteAdmin() throws Exception {
        // Le claim est repris tel quel : « ADMIN » ne devient pas ROLE_ADMIN
        assertThat(get("/test/admin", createToken(ISSUER, AUDIENCE, "ADMIN")).statusCode())
                .isEqualTo(403);
        assertThat(get("/test/admin", createToken(ISSUER, AUDIENCE, null)).statusCode())
                .isEqualTo(403);
    }

    @Test
    void unSubQuiNEstPasUnUuidDonne401EtJamais500() throws Exception {
        var token = createToken("pas-un-uuid", ISSUER, AUDIENCE, ROLE_CLIENT);

        var response = get("/customers/me", token);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow())
                .contains("application/problem+json");
        assertThat(parseJson(response).get("code").asString()).isEqualTo("INVALID_TOKEN");
        assertThat(get("/customers/" + UUID.randomUUID(), token).statusCode()).isEqualTo(401);
    }

    private static String createToken(String issuer, String audience, String role)
            throws Exception {
        return createToken(SUBJECT, issuer, audience, role);
    }
}
