package com.coretrust.bank.dispute.repository;

import com.coretrust.bank.dispute.entity.Dispute;
import com.coretrust.bank.dispute.entity.DisputeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DisputeRepository extends JpaRepository<Dispute, Long> {

    boolean existsByTransactionIdAndStatus(Long transactionId, DisputeStatus status);

    @EntityGraph(attributePaths = "transaction")
    Page<Dispute> findByStatus(DisputeStatus status, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "transaction")
    Page<Dispute> findAll(Pageable pageable);
}