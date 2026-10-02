package com.coretrust.bank.otp.repository;

import com.coretrust.bank.otp.entity.Otp;
import com.coretrust.bank.otp.entity.OtpPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OtpRepository extends JpaRepository<Otp, Long> {

    /** A new OTP replaces any unused one for the same customer and purpose. */
    @Modifying
    @Query("update Otp o set o.consumed = true where o.user.id = :userId and o.purpose = :purpose and o.consumed = false")
    int invalidateActive(@Param("userId") Long userId, @Param("purpose") OtpPurpose purpose);
}