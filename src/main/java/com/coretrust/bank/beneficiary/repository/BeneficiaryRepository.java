package com.coretrust.bank.beneficiary.repository;

import com.coretrust.bank.beneficiary.entity.Beneficiary;
import com.coretrust.bank.beneficiary.entity.BeneficiaryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BeneficiaryRepository extends JpaRepository<Beneficiary, Long> {

    List<Beneficiary> findByUserIdAndStatus(Long userId, BeneficiaryStatus status);

    Optional<Beneficiary> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndAccountNumber(Long userId, String accountNumber);

    Page<Beneficiary> findByUserIdAndStatus(Long userId, BeneficiaryStatus status, Pageable pageable);

    Optional<Beneficiary> findByUserIdAndAccountNumber(Long userId, String accountNumber);
}
