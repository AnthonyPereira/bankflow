package com.bankflow.auth.application.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bankflow.auth.application.user.exception.InvalidSessionException;
import com.bankflow.auth.domain.user.Role;
import com.bankflow.auth.domain.user.User;
import com.bankflow.auth.domain.user.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CurrentUserServiceTests {

    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");

    private final UserRepository users = mock(UserRepository.class);
    private final CurrentUserService service = new CurrentUserService(users);

    @Test
    void renvoieUnUtilisateurActif() {
        var user = User.register("client@bankflow.test", "{bcrypt}x", Role.ROLE_CLIENT, NOW);
        when(users.findById(user.getId())).thenReturn(Optional.of(user));

        assertThat(service.getCurrentUser(user.getId())).isSameAs(user);
    }

    @Test
    void refuseUnUtilisateurQuiNExistePlus() {
        var unknown = UUID.randomUUID();
        when(users.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCurrentUser(unknown))
                .isInstanceOf(InvalidSessionException.class)
                .hasMessage("Session invalide. Veuillez vous reconnecter.");
    }

    @Test
    void refuseUnUtilisateurDesactive() {
        var user = User.register("client@bankflow.test", "{bcrypt}x", Role.ROLE_CLIENT, NOW);
        user.disable(NOW);
        when(users.findById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.getCurrentUser(user.getId()))
                .isInstanceOf(InvalidSessionException.class);
    }
}
