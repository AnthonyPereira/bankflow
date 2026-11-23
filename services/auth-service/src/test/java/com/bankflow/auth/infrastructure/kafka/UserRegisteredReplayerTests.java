package com.bankflow.auth.infrastructure.kafka;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.bankflow.auth.domain.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

class UserRegisteredReplayerTests {

    @Test
    void sansLaProprieteDeRejeuRienNEstPublie() {
        var users = mock(UserRepository.class);
        var publisher = mock(UserRegisteredPublisher.class);
        var replayer =
                new UserRegisteredReplayer(
                        users, publisher, mock(PlatformTransactionManager.class), false);

        replayer.run(null);

        verifyNoInteractions(users, publisher);
    }
}
