package com.coretrust.bank.transaction.repository;

import com.coretrust.bank.transaction.entity.ReversalRequest;
import com.coretrust.bank.transaction.entity.ReversalRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReversalRequestRepository extends JpaRepository<ReversalRequest, Long> {

    boolean existsByTransactionIdAndStatus(Long transactionId, ReversalRequestStatus status);

    @EntityGraph(attributePaths = "transaction")
    Page<ReversalRequest> findByStatus(ReversalRequestStatus status, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "transaction")
    Page<ReversalRequest> findAll(Pageable pageable);

    @Query("""
            select count(r) > 0 from ReversalRequest r
            join r.transaction t
            left join t.fromAccount fa
            left join t.toAccount ta
            where r.status = :status and (fa.id = :accountId or ta.id = :accountId)
            """)
    boolean existsForAccountWithStatus(@Param("accountId") Long accountId,
                                       @Param("status") ReversalRequestStatus status);
}