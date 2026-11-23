package com.bankflow.auth.application.registration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bankflow.auth.application.demo.DemoAccounts;
import com.bankflow.auth.domain.user.User;
import com.bankflow.auth.domain.user.UserRepository;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

class RegistrationServiceTests {

    private static final String EMAIL = "client@bankflow.test";
    private static final String PASSWORD = "un-mot-de-passe-solide";

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);

    @Test
    void lesAdressesDeDemonstrationSontReserveesMemeQuandLaDemonstrationEstCoupee() {
        // Le compte n'existe pas encore : le seeder ne l'a jamais créé
        var service = createService(new DemoAccounts(false));

        assertThatThrownBy(() -> service.register(" Demo.Admin@BankFlow.local ", "un-mot-de-passe"))
                .isInstanceOf(EmailAlreadyUsedException.class);
        verify(users, never()).saveAndFlush(any(User.class));
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void unDoublonCreeParUneInscriptionSimultaneeRepond409() {
        when(users.saveAndFlush(any(User.class))).thenThrow(createViolation("uk_users_email"));

        assertThatThrownBy(() -> createService(new DemoAccounts(true)).register(EMAIL, PASSWORD))
                .isInstanceOf(EmailAlreadyUsedException.class);
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void uneAutreViolationDeContrainteNEstPasPriseePourUnDoublon() {
        var violation = createViolation("ck_users_role");
        when(users.saveAndFlush(any(User.class))).thenThrow(violation);

        assertThatThrownBy(() -> createService(new DemoAccounts(true)).register(EMAIL, PASSWORD))
                .isSameAs(violation);
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void uneViolationSansNomDeContrainteNEstPasPriseePourUnDoublon() {
        var violation = new DataIntegrityViolationException("valeur trop longue");
        when(users.saveAndFlush(any(User.class))).thenThrow(violation);

        assertThatThrownBy(() -> createService(new DemoAccounts(true)).register(EMAIL, PASSWORD))
                .isSameAs(violation);
    }

    private RegistrationService createService(DemoAccounts demoAccounts) {
        return new RegistrationService(
                users,
                passwordEncoder,
                events,
                Clock.fixed(Instant.parse("2026-11-20T10:00:00Z"), ZoneOffset.UTC),
                demoAccounts);
    }

    /** Forme de l'exception que Spring traduit depuis Hibernate et PostgreSQL. */
    private static DataIntegrityViolationException createViolation(String constraintName) {
        return new DataIntegrityViolationException(
                "violation",
                new ConstraintViolationException(
                        "violation", new SQLException("violation", "23505"), constraintName));
    }
}
