package com.bankflow.banking.domain.account;

import com.bankflow.banking.domain.money.Currency;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository
        extends JpaRepository<Account, UUID>, JpaSpecificationExecutor<Account> {

    Page<Account> findByCustomerId(UUID customerId, Pageable pageable);

    long countByCustomerIdAndStatus(UUID customerId, AccountStatus status);

    long countByStatus(AccountStatus status);

    /** Total des soldes et nombre de comptes ouverts par devise, calculés par la base. */
    @Query(
            """
            select a.currency as currency, sum(a.balance) as balance, count(a) as openAccounts
            from Account a
            where a.status = com.bankflow.banking.domain.account.AccountStatus.ACTIVE
            group by a.currency
            order by a.currency
            """)
    List<CurrencyTotal> sumOpenBalancesByCurrency();

    /** Agrégat d'une devise : projection de la requête ci-dessus. */
    interface CurrencyTotal {
        Currency getCurrency();

        BigDecimal getBalance();

        long getOpenAccounts();
    }

    /** Identifiant seul : ne charge pas le compte, donc ne fige pas son état avant le verrou. */
    @Query("select a.id from Account a where a.accountNumber = :accountNumber")
    Optional<UUID> findIdByAccountNumber(@Param("accountNumber") String accountNumber);

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
