package com.bankflow.banking.domain.account;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Page<Account> findByCustomerId(UUID customerId, Pageable pageable);

    long countByCustomerIdAndStatus(UUID customerId, AccountStatus status);

    /** Verrouille le compte pour sérialiser ses modifications (clôture, puis opérations). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Insère un compte sauf si son numéro est déjà pris. Une collision renvoie 0 sans faire échouer
     * la transaction en cours, ce qu'une exception de contrainte unique ferait.
     */
    @Modifying
    @Query(
            value =
                    """
                    INSERT INTO accounts (id, customer_id, account_number, label, currency,
                                          balance, status, version, created_at)
                    VALUES (:id, :customerId, :accountNumber, :label, :currency,
                            :balance, :status, 0, :createdAt)
                    ON CONFLICT (account_number) DO NOTHING
                    """,
            nativeQuery = true)
    int insertIfAccountNumberFree(
            @Param("id") UUID id,
            @Param("customerId") UUID customerId,
            @Param("accountNumber") String accountNumber,
            @Param("label") String label,
            @Param("currency") String currency,
            @Param("balance") BigDecimal balance,
            @Param("status") String status,
            @Param("createdAt") Instant createdAt);

    /**
     * Insère le compte tel quel, sauf si son numéro est déjà pris (renvoie alors {@code false}).
     */
    default boolean insertIfNumberFree(Account account) {
        return insertIfAccountNumberFree(
                        account.getId(),
                        account.getCustomerId(),
                        account.getAccountNumber(),
                        account.getLabel(),
                        account.getCurrency().name(),
                        account.getBalance().amount(),
                        account.getStatus().name(),
                        account.getCreatedAt())
                == 1;
    }
}
