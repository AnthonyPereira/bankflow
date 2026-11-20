package com.bankflow.auth.application.registration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.bankflow.auth.application.demo.DemoAccounts;
import com.bankflow.auth.domain.user.User;
import com.bankflow.auth.domain.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

class RegistrationServiceTests {

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);

    @Test
    void lesAdressesDeDemonstrationSontReserveesMemeQuandLaDemonstrationEstCoupee() {
        // Le compte n'existe pas encore : le seeder ne l'a jamais créé
        var service =
                new RegistrationService(
                        users,
                        passwordEncoder,
                        events,
                        Clock.fixed(Instant.parse("2026-11-20T10:00:00Z"), ZoneOffset.UTC),
                        new DemoAccounts(false));

        assertThatThrownBy(() -> service.register(" Demo.Admin@BankFlow.local ", "un-mot-de-passe"))
                .isInstanceOf(EmailAlreadyUsedException.class);
        verify(users, never()).saveAndFlush(any(User.class));
        verify(events, never()).publishEvent(any(Object.class));
    }
}
