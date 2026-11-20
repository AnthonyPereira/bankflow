package com.bankflow.auth.infrastructure.bootstrap;

import com.bankflow.auth.domain.user.Role;
import com.bankflow.auth.domain.user.User;
import com.bankflow.auth.domain.user.UserRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Crée le compte administrateur initial depuis l'environnement. Un compte existant n'est jamais
 * modifié, et un mot de passe trop faible n'est jamais utilisé.
 */
@Component
class AdminAccountSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountSeeder.class);
    private static final int MINIMUM_PASSWORD_LENGTH = 12;

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final String email;
    private final String password;

    AdminAccountSeeder(
            UserRepository users,
            PasswordEncoder passwordEncoder,
            Clock clock,
            @Value("${bankflow.admin.email}") String email,
            @Value("${bankflow.admin.password}") String password) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) {
            return;
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
