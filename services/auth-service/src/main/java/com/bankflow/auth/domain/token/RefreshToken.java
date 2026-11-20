package com.bankflow.auth.domain.token;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Empreinte d'un refresh token. Les jetons issus d'une même connexion forment une famille, dont la
 * date d'ouverture est recopiée à chaque rotation.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "family_created_at", nullable = false, updatable = false)
    private Instant familyCreatedAt;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RefreshToken() {}

    public RefreshToken(
            UUID userId,
            UUID familyId,
            Instant familyCreatedAt,
            String tokenHash,
            Instant expiresAt,
            Instant now) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.familyId = familyId;
        this.familyCreatedAt = familyCreatedAt;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = now;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpiredAt(Instant now) {
        return !expiresAt.isAfter(now);
    }

    /** Vrai si la session a dépassé sa durée de vie absolue, quelles que soient les rotations. */
    public boolean isSessionExpiredAt(Instant now, Duration maxSessionAge) {
        return !familyCreatedAt.plus(maxSessionAge).isAfter(now);
    }

    /** Vrai si le jeton a été remplacé il y a trop longtemps pour être une requête simultanée. */
    public boolean isReuseSuspectedAt(Instant now, Duration grace) {
        return isRevoked() && revokedAt.plus(grace).isBefore(now);
    }

    public void revoke(Instant now) {
        this.revokedAt = now;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public Instant getFamilyCreatedAt() {
        return familyCreatedAt;
    }
}
