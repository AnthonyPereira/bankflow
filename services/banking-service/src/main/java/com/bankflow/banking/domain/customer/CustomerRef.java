package com.bankflow.banking.domain.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Projection locale d'un client du Customer Service : seules les données utiles à la banque
 * (identité technique, email, statut) sont copiées, jamais les données personnelles.
 */
@Entity
@Table(name = "customer_refs")
public class CustomerRef {

    @Id
    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(nullable = false, length = 254)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CustomerStatus status;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CustomerRef() {}

    /**
     * {@code occurredAt} est la date de l'état publié par le Customer Service, pas l'horloge
     * locale.
     */
    public CustomerRef(
            UUID customerId, UUID userId, String email, CustomerStatus status, Instant occurredAt) {
        this.customerId = customerId;
        this.userId = userId;
        this.email = email;
        this.status = status;
        this.updatedAt = occurredAt;
    }

    /**
     * Un événement strictement plus ancien que l'état appliqué est périmé : le rejouer ferait
     * revenir en arrière (par exemple réactiver un client suspendu). À égalité, il est appliqué.
     */
    public boolean isStaleEvent(Instant occurredAt) {
        return occurredAt.isBefore(updatedAt);
    }

    /** Aligne la projection sur l'état complet publié à la date {@code occurredAt}. */
    public void applyState(
            UUID newUserId, String newEmail, CustomerStatus newStatus, Instant occurredAt) {
        this.userId = newUserId;
        this.email = newEmail;
        this.status = newStatus;
        this.updatedAt = occurredAt;
    }

    public boolean isSuspended() {
        return status == CustomerStatus.SUSPENDED;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public CustomerStatus getStatus() {
        return status;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
