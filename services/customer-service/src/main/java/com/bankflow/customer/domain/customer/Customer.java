package com.bankflow.customer.domain.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;

/** Profil bancaire d'un utilisateur, créé à partir de l'événement {@code UserRegistered}. */
@Entity
@Table(name = "customers")
public class Customer {

    @Id private UUID id;

    @Column(name = "user_id", nullable = false, unique = true, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(length = 20)
    private String phone;

    @Column(length = 255)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CustomerStatus status;

    /** Client de démonstration : il ne peut jamais être suspendu. */
    @Column(nullable = false)
    private boolean demo;

    /** Verrou optimiste : deux modifications simultanées ne s'écrasent pas en silence. */
    @Version private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Customer() {}

    private Customer(UUID userId, String email, boolean demo, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.email = normalizeEmail(email);
        this.status = CustomerStatus.ACTIVE;
        this.demo = demo;
        this.createdAt = truncate(now);
        this.updatedAt = truncate(now);
    }

    /** Crée le client d'un utilisateur qui vient de s'inscrire : actif, sans coordonnées. */
    public static Customer createFromUser(UUID userId, String email, boolean demo, Instant now) {
        return new Customer(userId, email, demo, now);
    }

    public static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    /** Remplace les coordonnées : une chaîne vide ou blanche devient {@code null}. */
    public void updateContact(
            String firstName, String lastName, String phone, String address, Instant now) {
        this.firstName = normalizeText(firstName);
        this.lastName = normalizeText(lastName);
        this.phone = normalizeText(phone);
        this.address = normalizeText(address);
        this.updatedAt = truncate(now);
    }

    /** Vrai tant qu'aucune coordonnée n'a été saisie. */
    public boolean hasEmptyProfile() {
        return firstName == null && lastName == null && phone == null && address == null;
    }

    /**
     * @throws DemoCustomerCannotBeSuspendedException pour un client de démonstration
     */
    public void suspend(Instant now) {
        if (demo) {
            throw new DemoCustomerCannotBeSuspendedException();
        }
        if (status != CustomerStatus.SUSPENDED) {
            this.status = CustomerStatus.SUSPENDED;
            this.updatedAt = truncate(now);
        }
    }

    public void activate(Instant now) {
        if (status != CustomerStatus.ACTIVE) {
            this.status = CustomerStatus.ACTIVE;
            this.updatedAt = truncate(now);
        }
    }

    /**
     * Passe le client en démonstration (jamais l'inverse). Un client de démonstration ne pouvant
     * pas être suspendu (contrainte {@code customers_demo_never_suspended}), il est réactivé s'il
     * l'était : l'appelant doit alors publier le changement de statut.
     */
    public void markAsDemo(Instant now) {
        if (!demo) {
            this.demo = true;
            this.status = CustomerStatus.ACTIVE;
            this.updatedAt = truncate(now);
        }
    }

    /**
     * PostgreSQL stocke les dates à la microseconde : tronquer à l'écriture garantit que la date
     * publiée dans les événements est identique à celle lue en base.
     */
    private static Instant truncate(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MICROS);
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        var stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getPhone() {
        return phone;
    }

    public String getAddress() {
        return address;
    }

    public CustomerStatus getStatus() {
        return status;
    }

    public boolean isDemo() {
        return demo;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
