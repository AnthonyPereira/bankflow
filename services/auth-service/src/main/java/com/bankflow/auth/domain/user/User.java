package com.bankflow.auth.domain.user;

import com.bankflow.auth.domain.user.exception.DemoAccountCannotBeDisabledException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/** Compte d'authentification. Le mot de passe n'existe ici que sous forme de hachage. */
@Entity
@Table(name = "users")
public class User {

    @Id private UUID id;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    private boolean enabled;

    /** Compte de démonstration au mot de passe public : il ne peut jamais être désactivé. */
    @Column(nullable = false, updatable = false)
    private boolean demo;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {}

    private User(UUID id, String email, String passwordHash, Role role, boolean demo, Instant now) {
        this.id = id;
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.role = role;
        this.enabled = true;
        this.demo = demo;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static User register(String email, String passwordHash, Role role, Instant now) {
        return new User(UUID.randomUUID(), email, passwordHash, role, false, now);
    }

    /**
     * Crée un compte de démonstration. Il suit les mêmes règles que les autres comptes, sauf qu'il
     * ne peut jamais être désactivé.
     */
    public static User registerDemo(String email, String passwordHash, Role role, Instant now) {
        return new User(UUID.randomUUID(), email, passwordHash, role, true, now);
    }

    /** Une adresse ne doit exister qu'une fois, quelle que soit sa casse. */
    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public boolean isLockedAt(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /**
     * Désactive le compte de façon permanente, contrairement au verrouillage temporaire après trop
     * d'échecs de connexion.
     *
     * @throws DemoAccountCannotBeDisabledException pour un compte de démonstration
     */
    public void disable(Instant now) {
        if (demo) {
            throw new DemoAccountCannotBeDisabledException();
        }
        this.enabled = false;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isDemo() {
        return demo;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
