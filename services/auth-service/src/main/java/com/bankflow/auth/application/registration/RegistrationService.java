package com.bankflow.auth.application.registration;

import com.bankflow.auth.application.demo.DemoAccounts;
import com.bankflow.auth.domain.user.Role;
import com.bankflow.auth.domain.user.User;
import com.bankflow.auth.domain.user.UserRegistered;
import com.bankflow.auth.domain.user.UserRepository;
import java.time.Clock;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Inscrit un nouvel utilisateur avec le rôle client. */
@Service
public class RegistrationService {

    private static final String EMAIL_UNIQUE_CONSTRAINT = "uk_users_email";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final DemoAccounts demoAccounts;

    RegistrationService(
            UserRepository users,
            PasswordEncoder passwordEncoder,
            ApplicationEventPublisher events,
            Clock clock,
            DemoAccounts demoAccounts) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
        this.clock = clock;
        this.demoAccounts = demoAccounts;
    }

    /**
     * Une adresse de compte de démonstration est refusée comme une adresse déjà utilisée, même
     * quand ce compte n'existe pas encore : la réponse ne révèle rien de plus.
     */
    @Transactional
    public User register(String email, String password) {
        if (demoAccounts.isDemoEmail(email) || users.existsByEmail(User.normalizeEmail(email))) {
            throw new EmailAlreadyUsedException();
        }
        var now = clock.instant();
        var user = User.register(email, passwordEncoder.encode(password), Role.ROLE_CLIENT, now);
        try {
            // saveAndFlush révèle ici un doublon créé par une inscription simultanée
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            // Toute autre violation (colonne trop longue, contrainte CHECK…) reste une erreur
            if (isEmailUniquenessViolation(exception)) {
                throw new EmailAlreadyUsedException();
            }
            throw exception;
        }
        events.publishEvent(
                new UserRegistered(user.getId(), user.getEmail(), false, user.getRole(), now));
        return user;
    }

    /** Contrainte {@code uk_users_email} de la migration V2. */
    private static boolean isEmailUniquenessViolation(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && EMAIL_UNIQUE_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}
