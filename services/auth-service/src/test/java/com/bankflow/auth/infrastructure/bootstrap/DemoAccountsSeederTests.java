package com.bankflow.auth.infrastructure.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bankflow.auth.application.demo.DemoAccounts;
import com.bankflow.auth.domain.user.Role;
import com.bankflow.auth.domain.user.User;
import com.bankflow.auth.domain.user.UserRepository;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

class DemoAccountsSeederTests {

    private final UserRepository users = mock(UserRepository.class);

    @Test
    void creeLeClientEtLAdministrateurDeDemonstration() {
        createSeeder(true).run(null);

        var saved = ArgumentCaptor.forClass(User.class);
        verify(users, times(2)).save(saved.capture());
        assertThat(saved.getAllValues())
                .extracting(User::getEmail, User::getRole)
                .containsExactly(
                        tuple("demo.client@bankflow.local", Role.ROLE_CLIENT),
                        tuple("demo.admin@bankflow.local", Role.ROLE_ADMIN));
        assertThat(saved.getAllValues())
                .allSatisfy(user -> {
                    assertThat(user.getPasswordHash()).startsWith("{bcrypt}");
                    assertThat(user.isDemo()).isTrue();
                    assertThat(user.isEnabled()).isTrue();
                });
    }

    @Test
    void neModifiePasUnCompteExistant() {
        when(users.existsByEmail(any())).thenReturn(true);

        createSeeder(true).run(null);

        verify(users, never()).save(any());
    }

    @Test
    void neCreeRienQuandLaDemonstrationEstDesactivee() {
        createSeeder(false).run(null);

        verify(users, never()).save(any());
    }

    private DemoAccountsSeeder createSeeder(boolean enabled) {
        return new DemoAccountsSeeder(
                new DemoAccounts(enabled),
                users,
                PasswordEncoderFactories.createDelegatingPasswordEncoder(),
                Clock.systemUTC());
    }
}
