package com.bankflow.banking.domain.account;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Page<Account> findByCustomerId(UUID customerId, Pageable pageable);

    long countByCustomerIdAndStatus(UUID customerId, AccountStatus status);

    boolean existsByAccountNumber(String accountNumber);
}
