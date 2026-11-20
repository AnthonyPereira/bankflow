package com.bankflow.auth.application.login;

import com.bankflow.auth.domain.user.User;
import com.bankflow.auth.domain.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Vérifie les identifiants et verrouille temporairement un compte après trop d'échecs. */
@Service
public class LoginService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final int maxAttempts;
    private final Duration lockDuration;
    private final String decoyHash;

    LoginService(
            UserRepository users,
            PasswordEncoder passwordEncoder,
            Clock clock,
            @Value("${bankflow.login.max-attempts}") int maxAttempts,
            @Value("${bankflow.login.lock-duration}") Duration lockDuration) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.maxAttempts = maxAttempts;
        this.lockDuration = lockDuration;
        this.decoyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * La ligne de l'utilisateur reste verrouillée entre la lecture du compteur et son écriture. Des
     * tentatives simultanées ne peuvent donc pas dépasser le nombre d'essais autorisé. Le compteur
     * d'échecs doit être validé malgré l'exception levée ensuite, d'où {@code noRollbackFor}.
     */
    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public User authenticate(String email, String password) {
        var user = users.findByEmailForUpdate(User.normalizeEmail(email)).orElse(null);
        // L'heure est lue après l'attente éventuelle du verrou
        var now = clock.instant();

        if (user == null || !user.isEnabled() || user.isLockedAt(now)) {
            // Le hachage inutile égalise le temps de réponse : il ne révèle pas si le compte existe
            passwordEncoder.matches(password, decoyHash);
            throw new InvalidCredentialsException();
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            users.recordFailedLogin(user.getId(), now, maxAttempts, now.plus(lockDuration));
            throw new InvalidCredentialsException();
        }
        if (user.getFailedLoginAttempts() > 0 || user.getLockedUntil() != null) {
            users.resetFailedLogins(user.getId(), now);
        }
        return user;
    }
}
