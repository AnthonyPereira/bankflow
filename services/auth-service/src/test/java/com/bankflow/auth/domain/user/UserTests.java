package com.bankflow.auth.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

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
    void unCompteDeDemonstrationGardeSonRoleSansPrivilegeParticulier() {
        var user =
                User.registerDemo(
                        "demo.admin@bankflow.local", "{bcrypt}x", Role.ROLE_ADMIN, CREATED);

        assertThat(user.getRole()).isEqualTo(Role.ROLE_ADMIN);
        assertThat(user.isLockedAt(LATER)).isFalse();
    }
}
