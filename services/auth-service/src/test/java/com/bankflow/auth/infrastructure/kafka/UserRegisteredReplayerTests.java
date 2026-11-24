package com.bankflow.auth.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bankflow.auth.domain.user.Role;
import com.bankflow.auth.domain.user.User;
import com.bankflow.auth.domain.user.UserRepository;
import com.bankflow.auth.domain.user.event.UserRegistered;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import org.springframework.transaction.PlatformTransactionManager;

class UserRegisteredReplayerTests {

    private static final Instant CREATED_AT = Instant.parse("2026-01-02T03:04:05Z");

    private final UserRepository users = mock(UserRepository.class);
    private final UserRegisteredPublisher publisher = mock(UserRegisteredPublisher.class);

    @ParameterizedTest
    @ValueSource(ints = {0, 2, 3, 5})
    void chaqueUtilisateurEstEnvoyeUneSeuleFoisDansLOrdre(int total) {
        var all = createUsers(total);
        stubPages(all);
        var sent = recordSends(Integer.MAX_VALUE);

        var count = createReplayer(true).replayAll();

        assertThat(count).isEqualTo(total);
        assertThat(sent)
                .extracting(UserRegistered::email)
                .containsExactlyElementsOf(all.stream().map(User::getEmail).toList());
    }

    @Test
    void unEchecDEnvoiArreteLeRejeuSansException() {
        stubPages(createUsers(5));
        var sent = recordSends(2); // le 3e envoi échoue

        var count = createReplayer(true).replayAll();

        assertThat(count).isEqualTo(2);
        assertThat(sent).hasSize(3);
    }

    @Test
    void unEchecDEnvoiNePropageAucuneException() {
        stubPages(createUsers(5));
        recordSends(2);

        assertThatCode(createReplayer(true)::replaySafely).doesNotThrowAnyException();
    }

    @Test
    void uneExceptionDuRepositoryNEstPasPropagee() {
        when(users.findAllBy(any(Pageable.class))).thenThrow(new IllegalStateException("base"));

        assertThatCode(createReplayer(true)::replaySafely).doesNotThrowAnyException();

        verifyNoInteractions(publisher);
    }

    @Test
    void sansLaProprieteDeRejeuRienNEstLuNiEnvoye() {
        createReplayer(false).startReplay();

        verifyNoInteractions(users, publisher);
    }

    @Test
    void lIndicateurDemoEtLaDateDeCreationSontTransmis() {
        var demo = User.registerDemo("demo@bankflow.local", "hash", Role.ROLE_CLIENT, CREATED_AT);
        stubPages(List.of(demo));
        var captor = ArgumentCaptor.forClass(UserRegistered.class);
        when(publisher.send(captor.capture())).thenReturn(true);

        createReplayer(true).replayAll();

        assertThat(captor.getValue())
                .isEqualTo(
                        new UserRegistered(
                                demo.getId(),
                                "demo@bankflow.local",
                                true,
                                Role.ROLE_CLIENT,
                                CREATED_AT));
    }

    private UserRegisteredReplayer createReplayer(boolean enabled) {
        return new UserRegisteredReplayer(
                users, publisher, mock(PlatformTransactionManager.class), enabled, 2);
    }

    private static List<User> createUsers(int count) {
        return IntStream.range(0, count)
                .mapToObj(i ->
                        User.register(
                                "user" + i + "@bankflow.local",
                                "hash",
                                Role.ROLE_CLIENT,
                                CREATED_AT))
                .toList();
    }

    private void stubPages(List<User> all) {
        when(users.findAllBy(any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Pageable pageable = invocation.getArgument(0);
                    var from = (int) Math.min(pageable.getOffset(), all.size());
                    var to = Math.min(from + pageable.getPageSize(), all.size());
                    return new SliceImpl<>(all.subList(from, to), pageable, to < all.size());
                });
    }

    /** Enregistre les envois ; le publisher échoue à partir du (limit + 1)-ième appel. */
    private List<UserRegistered> recordSends(int limit) {
        var sent = new ArrayList<UserRegistered>();
        when(publisher.send(any()))
                .thenAnswer(invocation -> {
                    sent.add(invocation.getArgument(0));
                    return sent.size() <= limit;
                });
        return sent;
    }
}
