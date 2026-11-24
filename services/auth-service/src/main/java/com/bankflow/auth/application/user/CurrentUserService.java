package com.bankflow.auth.application.user;

import com.bankflow.auth.application.user.exception.InvalidSessionException;
import com.bankflow.auth.domain.user.User;
import com.bankflow.auth.domain.user.UserRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Retrouve l'utilisateur désigné par un JWT d'accès encore valide. */
@Service
public class CurrentUserService {

    private final UserRepository users;

    CurrentUserService(UserRepository users) {
        this.users = users;
    }

    /**
     * Un JWT reste valable jusqu'à son expiration, même si le compte a été supprimé ou désactivé
     * entre-temps : la session est alors refusée.
     *
     * @throws InvalidSessionException si l'utilisateur n'existe plus ou est désactivé
     */
    @Transactional(readOnly = true)
    public User getCurrentUser(UUID userId) {
        return users.findById(userId)
                .filter(User::isEnabled)
                .orElseThrow(InvalidSessionException::new);
    }
}
