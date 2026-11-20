package com.bankflow.auth.application.login;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bankflow.auth.domain.user.Role;
import com.bankflow.auth.domain.user.User;
import com.bankflow.auth.domain.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class LoginServiceTests {

    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");
    private static final String EMAIL = "client@bankflow.test";
    private static final String PASSWORD = "un-mot-de-passe-solide";
    private static final String USER_HASH = "{bcrypt}hash-du-compte";
    private static final String DECOY_HASH = "{bcrypt}hash-leurre";

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = createPasswordEncoder();
    private final LoginService service =
            new LoginService(
                    users,
                    passwordEncoder,
                    Clock.fixed(NOW, ZoneOffset.UTC),
                    5,
                    Duration.ofMinutes(15));

    @Test
    void uneReservationRefuseeEffectueLeHachageLeurreSansRemettreLeCompteurAZero() {
        var user = User.register(EMAIL, USER_HASH, Role.ROLE_CLIENT, NOW);
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(users.reserveLoginAttempt(any(UUID.class), any(), anyInt(), any())).thenReturn(0);

        assertRejectedAfterDecoyHash();
        verify(users).reserveLoginAttempt(user.getId(), NOW, 5, NOW.plus(Duration.ofMinutes(15)));
    }

    @Test
    void unCompteInconnuEffectueLeHachageLeurre() {
        when(users.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertRejectedAfterDecoyHash();
        verify(users, never()).reserveLoginAttempt(any(), any(), anyInt(), any());
    }

    @Test
    void unCompteDesactiveEffectueLeHachageLeurre() {
        var user = User.register(EMAIL, USER_HASH, Role.ROLE_CLIENT, NOW);
        user.disable(NOW);
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        assertRejectedAfterDecoyHash();
        verify(users, never()).reserveLoginAttempt(any(), any(), anyInt(), any());
    }

    @Test
    void unCompteVerrouilleEffectueLeHachageLeurre() {
        var user = User.register(EMAIL, USER_HASH, Role.ROLE_CLIENT, NOW);
        ReflectionTestUtils.setField(user, "lockedUntil", NOW.plus(Duration.ofMinutes(10)));
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        assertRejectedAfterDecoyHash();
        verify(users, never()).reserveLoginAttempt(any(), any(), anyInt(), any());
    }

    private static PasswordEncoder createPasswordEncoder() {
        var encoder = mock(PasswordEncoder.class);
        when(encoder.encode(anyString())).thenReturn(DECOY_HASH);
        return encoder;
    }

    /** Refus identique à un mauvais mot de passe : le vrai hachage n'est jamais vérifié. */
    private void assertRejectedAfterDecoyHash() {
        assertThatThrownBy(() -> service.authenticate(" Client@BankFlow.test ", PASSWORD))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Email ou mot de passe incorrect.");
        verify(passwordEncoder).matches(PASSWORD, DECOY_HASH);
        verify(passwordEncoder, never()).matches(PASSWORD, USER_HASH);
        verify(users, never()).resetFailedLogins(any(), any());
    }
}
