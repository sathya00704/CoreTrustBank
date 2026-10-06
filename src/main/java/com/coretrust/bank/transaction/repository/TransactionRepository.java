package com.coretrust.bank.transaction.repository;

import com.coretrust.bank.transaction.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Optional;

/** JpaSpecificationExecutor is here for the type/status/date filters on GET /transactions later. */
public interface TransactionRepository extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

    Optional<Transaction> findByTransactionReference(String transactionReference);

    /** Same query as the inherited one, but loads accounts and owners in the same SQL: no N+1. */
    @Override
    @EntityGraph(attributePaths = {"fromAccount.user", "toAccount.user", "originalTransaction"})
    Page<Transaction> findAll(Specification<Transaction> spec, Pageable pageable);
}
