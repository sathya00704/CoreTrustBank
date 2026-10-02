package com.coretrust.bank.account.repository;

import com.coretrust.bank.account.entity.UnfreezeRequest;
import com.coretrust.bank.account.entity.UnfreezeRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UnfreezeRequestRepository extends JpaRepository<UnfreezeRequest, Long> {

    boolean existsByAccountIdAndStatus(Long accountId, UnfreezeRequestStatus status);

    @EntityGraph(attributePaths = "account")
    Page<UnfreezeRequest> findByStatus(UnfreezeRequestStatus status, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "account")
    Page<UnfreezeRequest> findAll(Pageable pageable);

    Optional<UnfreezeRequest> findByAccountIdAndStatus(Long accountId, UnfreezeRequestStatus status);
}