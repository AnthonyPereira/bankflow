package com.bankflow.auth.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bankflow.auth.domain.Role;
import com.bankflow.auth.domain.User;
import com.bankflow.auth.domain.UserRepository;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

class AdminAccountSeederTests {

    private static final String STRONG_PASSWORD = "un-mot-de-passe-solide";

    private final UserRepository users = mock(UserRepository.class);

    @Test
    void creeLeCompteAdministrateurAvecUnMotDePasseHache() {
        seeder("Admin@BankFlow.local", STRONG_PASSWORD).run(null);

        var saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("admin@bankflow.local");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.ROLE_ADMIN);
        assertThat(saved.getValue().getPasswordHash()).startsWith("{bcrypt}");
    }

    @Test
    void neModifiePasUnCompteExistant() {
        when(users.existsByEmail("admin@bankflow.local")).thenReturn(true);

        seeder("admin@bankflow.local", STRONG_PASSWORD).run(null);

        verify(users, never()).save(any());
    }

    @Test
    void ignoreUnMotDePasseTropFaible() {
        seeder("admin@bankflow.local", "change-me").run(null);

        verify(users, never()).save(any());
    }

    @Test
    void neFaitRienSansConfiguration() {
        seeder("", "").run(null);

        verify(users, never()).save(any());
    }

    private AdminAccountSeeder seeder(String email, String password) {
        return new AdminAccountSeeder(
                users,
                PasswordEncoderFactories.createDelegatingPasswordEncoder(),
                Clock.systemUTC(),
                email,
                password);
    }
}
