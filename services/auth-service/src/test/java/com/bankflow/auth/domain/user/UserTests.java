package com.bankflow.auth.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bankflow.auth.domain.user.exception.DemoAccountCannotBeDisabledException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class UserTests {

    private static final Instant CREATED = Instant.parse("2026-11-01T10:00:00Z");
    private static final Instant LATER = Instant.parse("2026-11-02T10:00:00Z");

    @Test
    void unCompteOrdinaireEstDesactiveDefinitivement() {
        var user = User.register("Client@BankFlow.test", "{bcrypt}x", Role.ROLE_CLIENT, CREATED);

        user.disable(LATER);

        assertThat(user.isDemo()).isFalse();
        assertThat(user.isEnabled()).isFalse();
        assertThat(user.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    void unCompteDeDemonstrationNePeutJamaisEtreDesactive() {
        var user =
                User.registerDemo(
                        "demo.admin@bankflow.local", "{bcrypt}x", Role.ROLE_ADMIN, CREATED);

        assertThatThrownBy(() -> user.disable(LATER))
                .isInstanceOf(DemoAccountCannotBeDisabledException.class)
                .hasMessage("Un compte de démonstration ne peut pas être désactivé.");
        assertThat(user.isDemo()).isTrue();
        assertThat(user.isEnabled()).isTrue();
        assertThat(user.getUpdatedAt()).isEqualTo(CREATED);
    }

    @Test
    void unCompteDeDemonstrationGardeLeRoleDemande() {
        var admin =
                User.registerDemo(
                        "demo.admin@bankflow.local", "{bcrypt}x", Role.ROLE_ADMIN, CREATED);
        var client =
                User.registerDemo(
                        "demo.client@bankflow.local", "{bcrypt}x", Role.ROLE_CLIENT, CREATED);

        assertThat(admin.getRole()).isEqualTo(Role.ROLE_ADMIN);
        assertThat(client.getRole()).isEqualTo(Role.ROLE_CLIENT);
    }

    /** Aucun privilège particulier : le verrou temporaire s'applique comme à tout compte. */
    @Test
    void unCompteDeDemonstrationVerrouilleEstTraiteCommeUnCompteOrdinaire() {
        var demo =
                User.registerDemo(
                        "demo.admin@bankflow.local", "{bcrypt}x", Role.ROLE_ADMIN, CREATED);
        var ordinary = User.register("admin@bankflow.test", "{bcrypt}x", Role.ROLE_ADMIN, CREATED);
        for (var user : List.of(demo, ordinary)) {
            ReflectionTestUtils.setField(user, "lockedUntil", LATER);
        }

        for (var user : List.of(demo, ordinary)) {
            assertThat(user.isLockedAt(CREATED)).isTrue();
            assertThat(user.isLockedAt(LATER)).isFalse();
        }
    }
}
