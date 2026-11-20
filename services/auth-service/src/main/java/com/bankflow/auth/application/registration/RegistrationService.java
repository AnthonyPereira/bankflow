package com.bankflow.auth.application.registration;

import com.bankflow.auth.domain.user.Role;
import com.bankflow.auth.domain.user.User;
import com.bankflow.auth.domain.user.UserRegistered;
import com.bankflow.auth.domain.user.UserRepository;
import java.time.Clock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Inscrit un nouvel utilisateur avec le rôle client. */
@Service
public class RegistrationService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    RegistrationService(
            UserRepository users,
            PasswordEncoder passwordEncoder,
            ApplicationEventPublisher events,
            Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public User register(String email, String password) {
        if (users.existsByEmail(User.normalizeEmail(email))) {
            throw new EmailAlreadyUsedException();
        }
        var now = clock.instant();
        var user = User.register(email, passwordEncoder.encode(password), Role.ROLE_CLIENT, now);
        try {
            // saveAndFlush révèle ici un doublon créé par une inscription simultanée
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new EmailAlreadyUsedException();
        }
        events.publishEvent(new UserRegistered(user.getId(), user.getEmail(), now));
        return user;
    }
}
