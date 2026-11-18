package com.bankflow.auth.application;

import com.bankflow.auth.domain.Role;
import com.bankflow.auth.domain.User;
import com.bankflow.auth.domain.UserRepository;
import java.time.Clock;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Inscrit un nouvel utilisateur avec le rôle client. */
@Service
public class RegistrationService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    RegistrationService(UserRepository users, PasswordEncoder passwordEncoder, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public User register(String email, String password) {
        if (users.existsByEmail(User.normalizeEmail(email))) {
            throw new EmailAlreadyUsedException();
        }
        var user =
                User.register(
                        email, passwordEncoder.encode(password), Role.ROLE_CLIENT, clock.instant());
        try {
            // saveAndFlush révèle ici un doublon créé par une inscription simultanée
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new EmailAlreadyUsedException();
        }
    }
}
