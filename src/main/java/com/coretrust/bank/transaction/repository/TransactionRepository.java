package com.coretrust.bank.transaction.repository;

import com.coretrust.bank.transaction.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

import java.util.Optional;

/** JpaSpecificationExecutor is here for the type/status/date filters on GET /transactions later. */
public interface TransactionRepository extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

    Optional<Transaction> findByTransactionReference(String transactionReference);

    /** Same query as the inherited one, but loads accounts and owners in the same SQL: no N+1. */
    @Override
    @EntityGraph(attributePaths = {"fromAccount.user", "toAccount.user", "originalTransaction"})
    Page<Transaction> findAll(Specification<Transaction> spec, Pageable pageable);

    /** All transactions of one account in [start, end), oldest first, with accounts and owners loaded. */
    @Query("""
            select t from Transaction t
            left join fetch t.fromAccount fa left join fetch fa.user
            left join fetch t.toAccount ta left join fetch ta.user
            left join fetch t.originalTransaction
            where (fa.id = :accountId or ta.id = :accountId)
              and t.createdAt >= :start and t.createdAt < :end
            order by t.createdAt asc, t.id asc
            """)
    List<Transaction> findStatementLines(@Param("accountId") Long accountId,
                                         @Param("start") Instant start, @Param("end") Instant end);

    /** The account's latest transaction before a moment; its balance-after is the statement's opening balance. */
    @Query("""
            select t from Transaction t
            left join t.fromAccount fa left join t.toAccount ta
            where (fa.id = :accountId or ta.id = :accountId) and t.createdAt < :before
            order by t.createdAt desc, t.id desc
            """)
    List<Transaction> findLatestBefore(@Param("accountId") Long accountId, @Param("before") Instant before,
                                       Pageable pageable);
}
