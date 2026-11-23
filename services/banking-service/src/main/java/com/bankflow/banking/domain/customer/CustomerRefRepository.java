package com.bankflow.banking.domain.customer;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRefRepository extends JpaRepository<CustomerRef, UUID> {

    Optional<CustomerRef> findByUserId(UUID userId);

    /** Retrouve les références d'un email (celui du client de démonstration). */
    List<CustomerRef> findAllByEmailIgnoreCase(String email);

    long countByStatus(CustomerStatus status);

    /**
     * Verrouille la ligne d'un client retrouvé par son identifiant (ouverture par un conseiller).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CustomerRef c where c.customerId = :customerId")
    Optional<CustomerRef> findByCustomerIdForUpdate(@Param("customerId") UUID customerId);

    /** Verrouille la ligne pour sérialiser les ouvertures de compte d'un même client. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CustomerRef c where c.userId = :userId")
    Optional<CustomerRef> findByUserIdForUpdate(@Param("userId") UUID userId);
}
