package com.bankflow.auth.domain.token;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /** Le verrou empêche deux rotations simultanées du même jeton. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Vrai tant qu'un jeton de la famille n'est pas révoqué : la session reste ouverte. */
    boolean existsByFamilyIdAndRevokedAtIsNull(UUID familyId);

    /** Les jetons déjà révoqués gardent leur date et leur motif de révocation. */
    @Modifying
    @Query(
            """
            UPDATE RefreshToken t SET t.revokedAt = :now, t.revokedReason = :reason
            WHERE t.familyId = :familyId AND t.revokedAt IS NULL
            """)
    void revokeFamily(
            @Param("familyId") UUID familyId,
            @Param("now") Instant now,
            @Param("reason") RevocationReason reason);

    /**
     * Supprime les jetons qui ne servent plus. Un jeton révoqué reste en base tant que sa famille
     * peut être active, même après son expiration : sa réutilisation doit encore révoquer la
     * famille. Une famille ouverte avant {@code sessionStartLimit} a dépassé sa durée de vie
     * absolue et disparaît entièrement. Un jeton non révoqué mais expiré ne sert plus à rien.
     */
    @Modifying
    @Query(
            """
            DELETE FROM RefreshToken t
            WHERE t.familyCreatedAt < :sessionStartLimit
                OR (t.revokedAt IS NULL AND t.expiresAt < :now)
            """)
    int deleteObsolete(
            @Param("now") Instant now, @Param("sessionStartLimit") Instant sessionStartLimit);
}
