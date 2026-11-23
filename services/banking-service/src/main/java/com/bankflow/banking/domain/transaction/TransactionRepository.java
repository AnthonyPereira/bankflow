package com.bankflow.banking.domain.transaction;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TransactionRepository
        extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    Optional<Transaction> findByCompensatesTransactionId(UUID compensatesTransactionId);

    List<Transaction> findByCompensatesTransactionIdIn(Collection<UUID> compensatesTransactionIds);

    boolean existsByCompensatesTransactionId(UUID compensatesTransactionId);
}
