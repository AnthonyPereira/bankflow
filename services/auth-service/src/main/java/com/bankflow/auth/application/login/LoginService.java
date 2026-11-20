package com.bankflow.auth.application.login;

import com.bankflow.auth.domain.user.User;
import com.bankflow.auth.domain.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Vérifie les identifiants et verrouille temporairement un compte après trop d'échecs.
 *
 * <p>Aucun verrou de ligne ni aucune connexion à la base n'est tenu pendant le hachage du mot de
 * passe : une rafale de connexions sur un même compte ne peut pas saturer le pool de connexions.
 * Chaque tentative est d'abord réservée par un {@code UPDATE} atomique, qui la compte comme un
 * échec et refuse la réservation si le compte est verrouillé. Des tentatives simultanées ne peuvent
 * donc jamais vérifier plus de {@code max-attempts} mots de passe avant le verrouillage.
 */
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
     * Les trois cas d'échec (compte inconnu, compte verrouillé, mauvais mot de passe) répondent de
     * la même façon et dans un temps comparable, grâce au hachage leurre.
     */
    public User authenticate(String email, String password) {
        // Une seule lecture de l'horloge : le verrou est jugé à l'instant où il est posé
        var now = clock.instant();
        var user = users.findByEmail(User.normalizeEmail(email)).orElse(null);
        if (user == null || !user.isEnabled() || user.isLockedAt(now)) {
            throw createRejectionAfterDecoyHash(password);
        }

        // La tentative est comptée avant la vérification : un échec n'a plus rien à écrire
        var reserved =
                users.reserveLoginAttempt(user.getId(), now, maxAttempts, now.plus(lockDuration));
        if (reserved == 0) {
            // Une requête concurrente vient de verrouiller ou de désactiver le compte
            throw createRejectionAfterDecoyHash(password);
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        users.resetFailedLogins(user.getId(), now);
        return user;
    }

    /** Le hachage inutile égalise le temps de réponse : il ne révèle pas si le compte existe. */
    private InvalidCredentialsException createRejectionAfterDecoyHash(String password) {
        passwordEncoder.matches(password, decoyHash);
        return new InvalidCredentialsException();
    }
}
