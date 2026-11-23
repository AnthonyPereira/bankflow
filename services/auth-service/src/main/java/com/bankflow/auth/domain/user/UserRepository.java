package com.bankflow.auth.domain.user;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * Réserve une tentative de connexion en la comptant d'avance comme un échec, dans une
     * transaction courte : le verrou de ligne n'est tenu que le temps de l'{@code UPDATE}, jamais
     * pendant le hachage du mot de passe. La tentative qui atteint {@code maxAttempts} verrouille
     * le compte, et les suivantes ne sont plus réservées tant que le verrou court. Un verrou expiré
     * repart d'un compteur à 1.
     *
     * <p>Des {@code UPDATE} simultanés sur une même ligne s'exécutent l'un après l'autre, et
     * PostgreSQL réévalue la clause {@code WHERE} après l'attente : une réservation ne peut pas
     * passer sur un compte que la précédente vient de verrouiller.
     *
     * @return 1 si la tentative est réservée, 0 si le compte est verrouillé ou désactivé
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query(
            value =
                    """
                    UPDATE users
                    SET failed_login_attempts = CASE
                            WHEN locked_until IS NOT NULL THEN 1
                            ELSE failed_login_attempts + 1
                        END,
                        locked_until = CASE
                            WHEN CASE
                                    WHEN locked_until IS NOT NULL THEN 1
                                    ELSE failed_login_attempts + 1
                                END >= :maxAttempts
                                THEN CAST(:lockedUntil AS TIMESTAMPTZ)
                            ELSE NULL
                        END,
                        updated_at = :now
                    WHERE id = :id
                        AND enabled
                        AND (locked_until IS NULL OR locked_until <= :now)
                    """,
            nativeQuery = true)
    int reserveLoginAttempt(
            @Param("id") UUID id,
            @Param("now") Instant now,
            @Param("maxAttempts") int maxAttempts,
            @Param("lockedUntil") Instant lockedUntil);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query(
            """
            UPDATE User u
            SET u.failedLoginAttempts = 0, u.lockedUntil = NULL, u.updatedAt = :now
            WHERE u.id = :id
            """)
    void resetFailedLogins(@Param("id") UUID id, @Param("now") Instant now);
}
