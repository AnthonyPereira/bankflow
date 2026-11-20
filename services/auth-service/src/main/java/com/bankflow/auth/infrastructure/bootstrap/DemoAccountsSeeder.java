package com.bankflow.auth.infrastructure.bootstrap;

import com.bankflow.auth.application.demo.DemoAccounts;
import com.bankflow.auth.domain.user.User;
import com.bankflow.auth.domain.user.UserRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Crée les comptes de démonstration absents, marqués comme tels pour qu'ils ne puissent jamais être
 * désactivés. Un compte existant n'est jamais modifié. Avec {@code DEMO_ACCOUNTS_ENABLED=false},
 * plus aucun compte n'est créé, mais ceux qui existent restent actifs.
 */
@Component
class DemoAccountsSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoAccountsSeeder.class);

    private final DemoAccounts demoAccounts;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    DemoAccountsSeeder(
            DemoAccounts demoAccounts,
            UserRepository users,
            PasswordEncoder passwordEncoder,
            Clock clock) {
        this.demoAccounts = demoAccounts;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (var account : demoAccounts.list()) {
            if (users.existsByEmail(User.normalizeEmail(account.email()))) {
                continue;
            }
            users.save(
                    User.registerDemo(
                            account.email(),
                            passwordEncoder.encode(account.password()),
                            account.role(),
                            clock.instant()));
            log.info("Compte de démonstration créé : {}", account.email());
        }
    }
}
