package com.bankflow.banking.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtActorsTests {

    private static Jwt createJwt(String role, Object demo) {
        var builder =
                Jwt.withTokenValue("jeton")
                        .header("alg", "none")
                        .subject(UUID.randomUUID().toString())
                        .claim("role", role)
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(60));
        if (demo != null) {
            builder.claim("demo", demo);
        }
        return builder.build();
    }

    @Test
    void unAdminDemoEstRestreintAuxClientsDemo() {
        var actor = JwtActors.createFrom(createJwt("ROLE_ADMIN", true));

        assertThat(actor.isAdmin()).isTrue();
        assertThat(actor.isRestrictedToDemo()).isTrue();
    }

    @Test
    void unVraiAdminOuUnClaimAbsentNeSontPasRestreints() {
        var real = JwtActors.createFrom(createJwt("ROLE_ADMIN", false));
        var absent = JwtActors.createFrom(createJwt("ROLE_ADMIN", null));

        assertThat(real.isRestrictedToDemo()).isFalse();
        assertThat(absent.isRestrictedToDemo()).isFalse();
    }

    @Test
    void unClientDemoNEstJamaisUnAdminRestreint() {
        var actor = JwtActors.createFrom(createJwt("ROLE_CLIENT", true));

        assertThat(actor.isAdmin()).isFalse();
        assertThat(actor.isRestrictedToDemo()).isFalse();
    }
}
