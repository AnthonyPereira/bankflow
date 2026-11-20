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
     * Compte un échec de façon atomique : des tentatives simultanées ne se perdent pas. Un verrou
     * expiré repart d'un compteur à 1.
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query(
            value =
                    """
                    UPDATE users
                    SET failed_login_attempts = CASE
                            WHEN locked_until IS NOT NULL AND locked_until <= :now THEN 1
                            ELSE failed_login_attempts + 1
                        END,
                        locked_until = CASE
                            WHEN locked_until IS NOT NULL AND locked_until <= :now THEN NULL
                            WHEN failed_login_attempts + 1 >= :maxAttempts
                                THEN CAST(:lockedUntil AS TIMESTAMPTZ)
                            ELSE locked_until
                        END,
                        updated_at = :now
                    WHERE id = :id
                    """,
            nativeQuery = true)
    void recordFailedLogin(
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
