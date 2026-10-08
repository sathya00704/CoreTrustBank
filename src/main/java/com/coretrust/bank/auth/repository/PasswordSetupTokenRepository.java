package com.coretrust.bank.auth.repository;

import com.coretrust.bank.auth.entity.PasswordSetupToken;
import com.coretrust.bank.auth.entity.PasswordTokenPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PasswordSetupTokenRepository extends JpaRepository<PasswordSetupToken, Long> {

    Optional<PasswordSetupToken> findByTokenHash(String tokenHash);

    /** A new link replaces older unused ones: only the latest email works. */
    @Modifying
    @Query("delete from PasswordSetupToken t where t.user.id = :userId and t.purpose = :purpose and t.usedAt is null")
    int deleteUnused(@Param("userId") Long userId, @Param("purpose") PasswordTokenPurpose purpose);
}