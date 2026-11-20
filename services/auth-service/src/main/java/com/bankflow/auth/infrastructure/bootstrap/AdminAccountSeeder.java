package com.bankflow.auth.infrastructure.bootstrap;

import com.bankflow.auth.application.demo.DemoAccounts;
import com.bankflow.auth.domain.user.Role;
import com.bankflow.auth.domain.user.User;
import com.bankflow.auth.domain.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Crée le compte administrateur initial depuis l'environnement. Un compte existant n'est jamais
 * modifié, et un mot de passe trop faible n'est jamais utilisé.
 *
 * <p>Une configuration impossible à appliquer fait échouer le démarrage avec un message clair :
 * l'exploitant a demandé un compte administrateur, l'ignorer en silence le laisserait croire qu'il
 * existe. C'est le cas d'un mot de passe de plus de 72 octets, que BCrypt refuse, et d'une adresse
 * réservée à un compte de démonstration, dont le mot de passe est public.
 *
 * <p>Il s'exécute avant {@link DemoAccountsSeeder} : le vrai compte administrateur est en place
 * avant les comptes publics.
 */
@Component
@Order(AdminAccountSeeder.ORDER)
class AdminAccountSeeder implements ApplicationRunner {

    static final int ORDER = 1;

    private static final Logger log = LoggerFactory.getLogger(AdminAccountSeeder.class);
    private static final int MINIMUM_PASSWORD_LENGTH = 12;
    private static final int BCRYPT_MAXIMUM_BYTES = 72;

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final DemoAccounts demoAccounts;
    private final String email;
    private final String password;

    AdminAccountSeeder(
            UserRepository users,
            PasswordEncoder passwordEncoder,
            Clock clock,
            DemoAccounts demoAccounts,
            @Value("${bankflow.admin.email}") String email,
            @Value("${bankflow.admin.password}") String password) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.demoAccounts = demoAccounts;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) {
            return;
        }
        if (demoAccounts.isDemoEmail(email)) {
            throw new IllegalStateException(
                    "ADMIN_EMAIL désigne un compte de démonstration, dont le mot de passe est"
                            + " public : choisissez une autre adresse.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAXIMUM_BYTES) {
            throw new IllegalStateException(
                    "ADMIN_PASSWORD dépasse 72 octets en UTF-8, la limite de BCrypt :"
                            + " choisissez un mot de passe plus court.");
        }
        if (password.length() < MINIMUM_PASSWORD_LENGTH) {
            log.warn("ADMIN_PASSWORD fait moins de 12 caractères : compte administrateur ignoré.");
            return;
        }
        if (users.existsByEmail(User.normalizeEmail(email))) {
            return;
        }
        users.save(
                User.register(
                        email, passwordEncoder.encode(password), Role.ROLE_ADMIN, clock.instant()));
        log.info("Compte administrateur initial créé.");
    }
}
