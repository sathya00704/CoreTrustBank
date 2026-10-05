package com.coretrust.bank.transaction.repository;

import com.coretrust.bank.transaction.entity.ReversalRequest;
import com.coretrust.bank.transaction.entity.ReversalRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReversalRequestRepository extends JpaRepository<ReversalRequest, Long> {

    boolean existsByTransactionIdAndStatus(Long transactionId, ReversalRequestStatus status);

    @EntityGraph(attributePaths = "transaction")
    Page<ReversalRequest> findByStatus(ReversalRequestStatus status, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "transaction")
    Page<ReversalRequest> findAll(Pageable pageable);
}