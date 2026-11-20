package com.bankflow.auth.application.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bankflow.auth.domain.token.RefreshToken;
import com.bankflow.auth.domain.token.RefreshTokenRepository;
import com.bankflow.auth.domain.user.Role;
import com.bankflow.auth.domain.user.User;
import com.bankflow.auth.domain.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Durée de vie absolue d'une session, vérifiée en faisant avancer une horloge. */
class RefreshTokenServiceTests {

    private static final Duration TTL = Duration.ofDays(7);
    private static final Duration MAX_SESSION_AGE = Duration.ofDays(30);
    private static final Instant LOGIN = Instant.parse("2026-11-01T08:00:00Z");

    private final RefreshTokenRepository tokens = mock(RefreshTokenRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final Map<String, RefreshToken> byHash = new HashMap<>();
    private final MutableClock clock = new MutableClock(LOGIN);
    private final User user =
            User.register("client@bankflow.test", "{bcrypt}x", Role.ROLE_CLIENT, LOGIN);

    private RefreshToken lastSaved;
    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        when(tokens.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> lastSaved = invocation.getArgument(0));
        when(tokens.findByTokenHash(anyString()))
                .thenAnswer(
                        invocation -> Optional.ofNullable(byHash.get(invocation.getArgument(0))));
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        service = createService(MAX_SESSION_AGE);
    }

    @Test
    void lesRotationsSuccessivesNeProlongentPasLaSessionAuDelaDeTrenteJours() {
        var current = remember(service.issue(user.getId()));

        // Une rotation tous les six jours : chaque jeton reste valable, la session non
        for (int day = 6; day < 30; day += 6) {
            clock.set(LOGIN.plus(Duration.ofDays(day)));
            current = remember(service.rotate(current.value()).refreshToken());
        }
        clock.set(LOGIN.plus(Duration.ofDays(29)));
        current = remember(service.rotate(current.value()).refreshToken());
        clock.set(LOGIN.plus(MAX_SESSION_AGE));

        var lastToken = current.value();
        assertThatThrownBy(() -> service.rotate(lastToken))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .extracting(
                        exception -> ((InvalidRefreshTokenException) exception).isSessionEnded())
                .isEqualTo(true);
    }

    @Test
    void leDernierJetonExpireALaFinDeLaSessionEtNonSeptJoursPlusTard() {
        var current = remember(service.issue(user.getId()));
        assertThat(current.timeToLive()).isEqualTo(TTL);
        for (int day = 6; day <= 18; day += 6) {
            clock.set(LOGIN.plus(Duration.ofDays(day)));
            current = remember(service.rotate(current.value()).refreshToken());
            assertThat(current.timeToLive()).isEqualTo(TTL);
        }

        clock.set(LOGIN.plus(Duration.ofDays(24)));
        var last = remember(service.rotate(current.value()).refreshToken());

        assertThat(last.timeToLive()).isEqualTo(Duration.ofDays(6));
    }

    @Test
    void unJetonEncoreValableEstRefuseQuandLaSessionADepasseSaDureeMaximale() {
        var issued = remember(service.issue(user.getId()));
        clock.set(LOGIN.plus(Duration.ofDays(6)));
        var rotated = remember(service.rotate(issued.value()).refreshToken());

        // La durée maximale a été réduite depuis : le jeton n'a pas expiré mais la session si
        clock.set(LOGIN.plus(Duration.ofDays(11)));
        var shorterSessions = createService(Duration.ofDays(10));

        assertThatThrownBy(() -> shorterSessions.rotate(rotated.value()))
                .isInstanceOf(InvalidRefreshTokenException.class);
        assertThat(lastSaved.isExpiredAt(clock.instant())).isFalse();
    }

    @Test
    void uneRotationConserveLaDateDOuvertureDeLaFamille() {
        var first = remember(service.issue(user.getId()));

        clock.set(LOGIN.plus(Duration.ofDays(5)));
        var second = remember(service.rotate(first.value()).refreshToken());

        assertThat(byHash.get(hash(second.value())).getFamilyCreatedAt()).isEqualTo(LOGIN);
    }

    private RefreshTokenService createService(Duration maxSessionAge) {
        return new RefreshTokenService(
                tokens, users, clock, TTL, Duration.ofSeconds(10), maxSessionAge);
    }

    /** Le dépôt simulé retrouve ensuite le jeton par son empreinte, comme la base. */
    private RefreshTokenService.IssuedRefreshToken remember(
            RefreshTokenService.IssuedRefreshToken issued) {
        byHash.put(hash(issued.value()), lastSaved);
        return issued;
    }

    private static String hash(String rawToken) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant now) {
            this.now = now;
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
